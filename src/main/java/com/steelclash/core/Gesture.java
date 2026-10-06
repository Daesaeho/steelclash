package com.steelclash.core;

/**
 * Experimental gesture attacks: while an attack button is held, the direction the mouse drags the view picks the
 * attack. What each direction does is configurable ({@link Mapping}); the default reads the drag as where the attack
 * comes from (as in Mordhau): left = slash from the left, right = slash from the right, up = overhead, down = stab.
 */
public final class Gesture {
    public enum Direction {
        LEFT, RIGHT, UP, DOWN
    }

    /** What a gesture direction does. {@code NONE} ignores that direction (the press acts like a plain click). */
    public enum Action {
        SLASH_FROM_LEFT(AttackType.SLASH, true),
        SLASH_FROM_RIGHT(AttackType.SLASH, false),
        OVERHEAD(AttackType.OVERHEAD, false),
        STAB(AttackType.STAB, false),
        KICK(AttackType.KICK, false),
        NONE(null, false);

        private final AttackType type;
        private final boolean mirrored;

        Action(AttackType type, boolean mirrored) {
            this.type = type;
            this.mirrored = mirrored;
        }

        public Result result() {
            return type == null ? null : new Result(type, mirrored);
        }
    }

    /** What each drag direction does. */
    public record Mapping(Action left, Action right, Action up, Action down) {
        public static final Mapping DEFAULT = new Mapping(Action.SLASH_FROM_LEFT, Action.SLASH_FROM_RIGHT, Action.OVERHEAD, Action.STAB);

        public Action get(Direction direction) {
            return switch (direction) {
                case LEFT -> left;
                case RIGHT -> right;
                case UP -> up;
                case DOWN -> down;
            };
        }
    }

    /** The attack a gesture asks for; {@code mirrored} = swings from the left (left to right). */
    public record Result(AttackType type, boolean mirrored) {
    }

    private Gesture() {
    }

    /**
     * @param yawDelta   degrees the view turned since the button went down (positive = right, as Minecraft yaw)
     * @param pitchDelta degrees the view tilted (positive = down, as Minecraft pitch)
     * @param threshold  how far the view must move before the gesture counts
     * @return the drag's dominant direction, or {@code null} while it is still too small to read
     */
    public static Direction direction(float yawDelta, float pitchDelta, float threshold) {
        float absYaw = Math.abs(yawDelta);
        float absPitch = Math.abs(pitchDelta);
        if (Math.max(absYaw, absPitch) < threshold) {
            return null;
        }
        if (absYaw >= absPitch) {
            return yawDelta < 0 ? Direction.LEFT : Direction.RIGHT;
        }
        return pitchDelta < 0 ? Direction.UP : Direction.DOWN;
    }

    /** The attack a drag asks for under {@code mapping}, or {@code null} (too small yet, or a direction set to NONE). */
    public static Result classify(float yawDelta, float pitchDelta, float threshold, Mapping mapping) {
        Direction direction = direction(yawDelta, pitchDelta, threshold);
        return direction == null ? null : mapping.get(direction).result();
    }
}
