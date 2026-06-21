package net.minecraft.client.renderer.state.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ParticlesRenderState {
    public final List<ParticleGroupRenderState> particles = new ArrayList<>();

    public void reset() {
        this.particles.forEach(ParticleGroupRenderState::clear);
        this.particles.clear();
    }

    public void add(final ParticleGroupRenderState state) {
        this.particles.add(state);
    }

    public void submit(final SubmitNodeStorage submitNodeStorage, final CameraRenderState camera) {
        for (ParticleGroupRenderState particle : this.particles) {
            particle.submit(submitNodeStorage, camera);
        }
    }
}
