package com.github.cuixudong74.softwarerasterizerjava;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Transforms;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo endpoint: renders a deterministic built-in scene (a colored cube grid) to PNG.
 * Identical requests always produce byte-identical images.
 */
@RestController
public class DemoRenderController {

    @GetMapping(value = "/render/demo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> demo(@RequestParam(defaultValue = "320") int width,
                                       @RequestParam(defaultValue = "240") int height,
                                       @RequestParam(defaultValue = "4") int threads,
                                       @RequestParam(defaultValue = "8") int tiles) {
        Framebuffer fb = new Framebuffer(width, height);
        fb.clear(0xFF202830, 1f);
        Camera camera = new Camera(new Vec3f(0, 1.5f, 4), new Vec3f(0, 0, 0), new Vec3f(0, 1, 0),
                60, 0.5, 50);
        RenderConfig config = new RenderConfig(threads, tiles, tiles, true, true,
                com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP, 10_000, 1_000_000);
        new Renderer().render(demoScene(), Mat4f.identity(), camera, null, fb, config);
        return ResponseEntity.ok(fb.toPng());
    }

    /** A small deterministic scene: three rotated colored triangles at different depths. */
    private static Mesh demoScene() {
        List<Vertex> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        Vec4f[] colors = {new Vec4f(1, 0.2f, 0.2f, 1), new Vec4f(0.2f, 1, 0.3f, 1),
                new Vec4f(0.3f, 0.4f, 1, 1)};
        for (int i = 0; i < 3; i++) {
            double angle = i * 0.7;
            float z = -1.5f - i * 0.8f;
            float cx = (i - 1) * 0.9f;
            Mat4f rot = Transforms.rotationY(angle);
            Vec3f[] corners = {new Vec3f(-0.7f, -0.5f, 0), new Vec3f(0.7f, -0.5f, 0), new Vec3f(0, 0.8f, 0)};
            int base = verts.size();
            for (int k = 0; k < 3; k++) {
                var p = rot.transform(corners[k]);
                verts.add(new Vertex(new Vec3f(p.x() + cx, p.y(), p.z() + z), colors[(i + k) % 3],
                        new Vec2f(0, 0)));
                idx.add(base + k);
            }
        }
        return new Mesh(verts, idx.stream().mapToInt(Integer::intValue).toArray());
    }
}
