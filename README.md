# software-rasterizer-java

纯进程内软件光栅化与裁剪管线：输入带变换的三角网格与相机参数，输出确定性位图。
不依赖图形设备与外部服务，所有结论与异常均可在本地复现。

## 构建与测试

```bash
mvn compile     # 编译
mvn test        # 运行全部自动化测试
mvn spring-boot:run   # 启动后访问 GET /render/demo?width=320&height=240 获取 PNG
```

## 管线总览

```
Mesh(模型空间) --校验--> 顶点变换(MVP) --齐次裁剪(6 平面)--> 透视除法+视口映射
    --> 分块光栅化(top-left 填充规则, 透视正确插值, 深度测试) --> Framebuffer(颜色+深度)
```

核心代码位于 `render/` 包：

| 类 | 职责 |
|---|---|
| `math/Vec2f,Vec3f,Vec4f,Mat4f` | 不可变数学类型，列主序矩阵（OpenGL 约定） |
| `core/Vertex,Mesh` | 顶点（位置+RGBA 颜色+UV）与索引三角网格 |
| `core/Camera` | 针孔相机参数 |
| `core/Framebuffer` | ARGB 颜色 + float 深度，尺寸上限 16384×16384 |
| `core/Texture,AddressMode` | 最近邻采样纹理，越界寻址行为明确 |
| `pipeline/Transforms` | 模型/视图/投影矩阵工厂 |
| `pipeline/Clipper` | 齐次裁剪空间 Sutherland-Hodgman 视锥裁剪 |
| `pipeline/Rasterizer` | 三角形填充、插值、深度测试 |
| `pipeline/Renderer` | 校验、管线编排、分块并行、取消/超时 |
| `pipeline/RenderConfig` | 全部可调配置项 |

## 坐标与深度约定

- **模型/世界/视图空间**：右手系。相机位于 `eye`，看向 `target`，视图空间下视线方向为 **-Z**，+Y 向上。
- **投影**：OpenGL 风格透视投影，可见区域为齐次裁剪空间 `-w <= x,y,z <= w`，
  透视除法后 NDC ∈ [-1,1]³。`near`/`far` 为沿视线方向的正距离。
- **视口映射**：`ndc.x ∈ [-1,1] → 屏幕 x ∈ [0,width]`（向右），
  `ndc.y ∈ [-1,1] → 屏幕 y ∈ [height,0]`（**y 向下翻转**，像素 (0,0) 在左上角）。
- **深度**：`depth = ndc.z * 0.5 + 0.5 ∈ [0,1]`，**0 = 近裁剪面，1 = 远裁剪面，小者胜出**。
  深度按 NDC z 在屏幕空间线性插值（这在透视投影下本身就是透视正确的）。
  深度比较为严格小于（`depth < buffer`），等深时先画的三角形保留，结果确定。
- **正面判定**：NDC 下逆时针（CCW）为正面；`backfaceCulling=true` 时剔除顺时针三角形。

## 填充规则（top-left rule）

像素中心 `(x+0.5, y+0.5)` 落在三角形内部（所有边函数 > 0）时被覆盖；
恰好落在边上（边函数 = 0）时，仅当该边是**顶边**（水平且内部在其下方）或
**左边**（内部在其右侧，即边向下走）时才被覆盖。对于绕序一致的网格，
共享边在两个相邻三角形中恰好只有一个是 top-left 边，因此：

- 共享边上的每个像素**恰好被覆盖一次**：不漏填、不重复覆盖；
- 结果与三角形绘制顺序、分块方式、线程数无关（逐像素确定）。

边函数使用 double 计算，零面积（退化/极细塌陷）三角形直接跳过。

## 裁剪

在齐次裁剪空间对全部 6 个视锥平面做 Sutherland-Hodgman 裁剪，裁剪交点处的
颜色与 UV 在线性（裁剪空间）意义下精确插值，因此**裁剪后再光栅化与逐像素裁剪
的可见结果一致**，跨越近裁剪面的三角形被切分而非整体丢弃。完全在视锥外
（含相机背后）的三角形被安全丢弃，不会经透视除法镜像到屏幕上。

## 顶点属性插值

颜色与 UV 使用 **1/w 透视正确插值**：屏幕空间线性插值 `attr/w` 与 `1/w`，
逐像素相除还原。纹理寻址越界行为由 `AddressMode` 明确定义：

- `CLAMP`：钳制到边缘纹素；
- `REPEAT`：小数回绕（`u' = u - floor(u)`）；
- UV 为 NaN 时定义为采样 (0,0) 纹素（不依赖未定义行为）。

## 并行与确定性

- 裁剪单线程按输入顺序执行；光栅化按 `tilesX × tilesY` 分块，每块拥有**互不相交**
  的像素矩形，块内仍按输入顺序处理三角形，因此每个像素只被一个线程写入。
- **任意线程数/分块数与单线程结果逐像素一致**（颜色与深度均按位相等），
  同一场景重复渲染亦逐像素一致。测试覆盖 1×1 到 64×64 分块与 1~8 线程。
- 渲染先写入私有缓冲，**成功后才整体提交**到目标 Framebuffer；
  取消/超时/异常不会留下半成品位图。

## 取消与超时

`timeoutMillis > 0` 时超时即抛出 `RenderCancelledException`：设置协作式取消标志，
工作线程在每个三角形处检查并 promptly 退出，线程池 `shutdownNow` 回收，
不泄漏线程（守护线程）。目标 Framebuffer 保持渲染前内容。

## 非法输入诊断

所有非法输入抛出 `InvalidInputException`，消息指明具体出错元素，例如：

- `vertex[3].position contains non-finite value NaN`（NaN/Inf 的位置、颜色、UV）；
- `index[5]=17 out of range [0,4)`（索引越界）、索引数非 3 的倍数；
- 相机：`near <= 0`、`far <= near`、`fov` 不在 (0,180)、`eye == target`、`up` 与视线平行；
- 帧缓冲尺寸超出 `[1, 16384]`；三角形数量超过 `maxTriangles`；
- 配置：`threads/tiles < 1`、负超时等。

非法数值永远不会悄悄传播到输出：渲染在写入目标缓冲前完成全部校验。

## 配置项（RenderConfig）

| 字段 | 默认 | 说明 |
|---|---|---|
| `threads` | CPU 核数 | 光栅化工作线程数（1 = 单线程） |
| `tilesX / tilesY` | 8 / 8 | 水平/垂直分块数 |
| `backfaceCulling` | true | 剔除背面（NDC 顺时针） |
| `depthTest` | true | 启用深度测试 |
| `addressMode` | CLAMP | 纹理越界寻址模式 |
| `timeoutMillis` | 0（不限） | 渲染超时，超时即取消且不提交结果 |
| `maxTriangles` | 4,000,000 | 输入及裁剪后三角形数量上限（内存安全阀） |

## 大规模场景稳定性

- 超大三角形（顶点远超屏幕）经裁剪后包围盒被钳制到块矩形，不会引发全屏扫描；
- 极细/退化三角形由零面积检查安全跳过；float 极值（有限）坐标不会导致崩溃；
- 内存上界：帧缓冲 ≤ 16384² × 8B，三角形数量受 `maxTriangles` 限制；
- 10 万三角形混合场景（含巨大/细条/屏外三角形）在 256×256、4 线程下秒级完成，
  耗时随输入规模受控增长（见 `StabilityTest`）。

## 测试覆盖（`src/test`，共 40+ 用例）

- `TransformsTest`：视图/投影矩阵的 NDC 边界与组合顺序；
- `ClipperTest`：平面内外切分、属性插值、全外包络、确定性；
- `RasterizerTest`：共享边恰好一次覆盖、无缝隙、深度遮挡与透视交叉、
  背面剔除、退化/屏外/超大三角形；
- `InterpolationTest`：颜色与 UV 的透视正确插值（与解析值对比）、纹理寻址模式；
- `ClipConsistencyTest`：裁剪结果与手工裁剪等价物逐像素一致、边界属性连续；
- `ParallelConsistencyTest`：多线程/多分块与单线程逐像素一致、重复渲染一致；
- `CancellationTest`：超时取消无半成品、无线程泄漏、取消后可继续渲染；
- `InvalidInputTest`：全部非法输入路径的诊断消息；
- `StabilityTest`：10 万三角形场景与 float 极值坐标；
- `DemoEndpointTest`：HTTP 端到端 PNG 输出逐字节确定。
