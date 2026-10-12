package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating;

import java.util.List;

/** Java compiler fixture for the public seating contract. */
public final class SeatApiJavaUsage {
    private SeatApiJavaUsage() {
    }

    /** Compiles the construction and implementation pattern available to Java integrations. */
    public static void compileUsage() {
        SeatIdentity identity = new SeatIdentity(
            "minecraft:overworld",
            "minecraft:entity",
            "00000000-0000-0000-0000-000000000001",
            "main"
        );
        SeatGeometry geometry = new SeatGeometry(0.0, 64.0, 0.0, 0.0f);
        SeatTarget<Object, Object> target = new SeatTarget<>() {
            @Override
            public SeatIdentity getIdentity() {
                return identity;
            }

            @Override
            public SeatGeometry geometry(Object world, Object rider) {
                return geometry;
            }

            @Override
            public SeatState inspect(Object world, Object rider) {
                return SeatState.AVAILABLE;
            }

            @Override
            public SeatState trySit(Object world, Object rider) {
                return SeatState.SEATED;
            }
        };
        SeatProvider<Object, Object> provider = (world, request) -> List.of(target);
        provider.resolve(new Object(), null);
    }
}
