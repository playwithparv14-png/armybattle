package com.armybattle;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Renders a soldier exactly like a real player (player model + armor + trims). */
public class SoldierRenderer extends BipedEntityRenderer<SoldierEntity, PlayerEntityModel<SoldierEntity>> {
    // Different default skins so the army doesn't look like clones
    private static final String[] SKINS = {"steve", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};

    public SoldierRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public Identifier getTexture(SoldierEntity entity) {
        int i = MathHelper.abs(entity.getUuid().hashCode()) % SKINS.length;
        return Identifier.ofVanilla("textures/entity/player/wide/" + SKINS[i] + ".png");
    }
}
