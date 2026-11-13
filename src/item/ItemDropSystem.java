package item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import item.ItemAPI.*;

/**
 * Owns spawning, downward wandering, wall reflection, magnet attraction, swept contact and offscreen removal.
 * No separate files, no Entity subclass, no inventory/effect changes, no game object references.
 * No event queue here. The manager receives spawn/expire/contact results and records them as events.
 */
class ItemDropSystem {
    private final ItemDefinitions definitions;
    private final Random random;
    private final ItemBalance balance;
    private final Map<Long, DroppedItem> items = new LinkedHashMap<Long, DroppedItem>();
    private LevelRules rules;
    private long nextDropId = 1;

    private static class DroppedItem {
        final long id;
        final ItemInfo info;
        double x, y, velocityX, velocityY, directionMillis;
        boolean magnetized;

        DroppedItem(long id, ItemInfo info, double x, double y) {
            this.id = id;
            this.info = info;
            this.x = x;
            this.y = y;
        }

        DropView view(LevelRules rules) {
            return new DropView(id, info,
                new Bounds(x, y, rules.pickupWidth, rules.pickupHeight), false, magnetized);
        }
    }

    ItemDropSystem(ItemDefinitions definitions, Random random) {
        this(definitions, ItemBalance.defaults(), random);
    }

    ItemDropSystem(ItemDefinitions definitions, ItemBalance balance, Random random) {
        this.definitions = ItemAPI.required(definitions, "definitions");
        this.balance = ItemAPI.required(balance, "balance");
        this.random = ItemAPI.required(random, "random");
    }

    /** Installs new validated rules and clears the drop list. The dropId counter is never reused. */
    void beginLevel(LevelRules rules) {
        this.rules = ItemAPI.required(rules, "rules");
        items.clear();
    }

    /**
     * Rolls one kind using the source's probability/weights, registers it, then returns an immutable View.
     * null only for a normal no-drop. p==0/1 consumes no probability roll. A single candidate skips the pick roll too.
     * Weight order is LinkedHashMap insertion order. Centers on cx/cy and clamps to the play area.
     * Regular 0.15 / special 1.0 are demo values; the actual values come from LevelRules.
     * Preventing duplicate defeat notices for the same enemy is the game's job.
     * stacks (itemId → current stack count) lowers stacking items' weights and removes them at max stacks.
     * No drop (null) when every candidate is removed.
     */
    DropView spawn(DropSource source, double cx, double cy, Map<String, Integer> stacks) {
        requireActive();
        ItemAPI.required(source, "source");
        ItemAPI.finite(cx, "cx");
        ItemAPI.finite(cy, "cy");
        ItemAPI.required(stacks, "stacks");
        DropRule rule = rules.dropRules.get(source);
        if (rule == null) throw new IllegalStateException("missing drop rule: " + source);
        if (rule.probability == 0) return null;
        if (rule.probability < 1 && random.nextDouble() >= rule.probability) return null;

        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        double total = 0;
        String selected = null;
        for (Map.Entry<String, Double> entry : rule.weights.entrySet()) {
            double weight = effectiveWeight(rule, entry.getKey(), entry.getValue(), stacks);
            if (weight > 0) {
                weights.put(entry.getKey(), weight);
                total += weight;
                selected = entry.getKey();
            }
        }
        if (!Double.isFinite(total)) throw new IllegalStateException("invalid drop weights");
        if (weights.isEmpty()) return null; // every remaining candidate is at max stacks
        if (weights.size() > 1) {
            double target = random.nextDouble() * total;
            double cumulative = 0;
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                cumulative += entry.getValue();
                if (target < cumulative) {
                    selected = entry.getKey();
                    break;
                }
            }
        }
        ItemInfo info = definitions.find(selected);
        if (info == null) throw new IllegalStateException("unknown item: " + selected);
        if (nextDropId == Long.MAX_VALUE) throw new IllegalStateException("drop IDs exhausted");
        double x = Math.max(rules.left, Math.min(rules.right - rules.pickupWidth,
            cx - rules.pickupWidth / 2));
        double y = Math.max(rules.top, Math.min(rules.floorY - rules.pickupHeight,
            cy - rules.pickupHeight / 2));
        DroppedItem item = new DroppedItem(nextDropId++, info, x, y);
        chooseDirection(item);
        items.put(item.id, item);
        return item.view(rules);
    }

    /**
     * Advances game time only (no wall clock). Splits wandering paths at every turn, wall,
     * bottom and magnet-range entry, so contact tests follow the actual path.
     * Contact before an offscreen exit wins; the manager then decides pickup/conversion.
     * Uncollectable ordinary drops keep moving and expire on a later update if necessary.
     */
    Frame advance(long delta, PlayerSnapshot player) { return advance(delta, player, false); }

    Frame advance(long delta, PlayerSnapshot player, boolean magnetEquipped) {
        requireActive();
        if (delta < 0) throw new IllegalArgumentException("negative delta");
        ItemAPI.required(player, "player");
        List<DropView> expired = new ArrayList<DropView>();
        List<DropView> contacts = new ArrayList<DropView>();
        Iterator<DroppedItem> iterator = items.values().iterator();
        while (iterator.hasNext()) {
            DroppedItem item = iterator.next();
            DropView contact = item.y >= rules.floorY ? null : move(item, delta, player, magnetEquipped);
            if (contact != null) {
                contacts.add(contact);
            } else if (item.y >= rules.floorY) {
                iterator.remove();
                expired.add(item.view(rules));
            }
        }
        return new Frame(expired, contacts);
    }

    private void chooseDirection(DroppedItem item) {
        item.velocityX = (random.nextBoolean() ? 1 : -1)
            * balance.horizontalSpeed * (0.5 + random.nextDouble() * 0.5);
        item.velocityY = rules.fallSpeed * (0.75 + random.nextDouble() * 0.5);
        item.directionMillis = balance.directionChangeMillis;
    }

    private DropView move(DroppedItem item, long delta, PlayerSnapshot player, boolean magnetEquipped) {
        boolean attract = magnetEquipped && player.canPickup;
        double targetX = player.bounds.x + player.bounds.width / 2 - rules.pickupWidth / 2;
        double targetY = player.bounds.y + player.bounds.height / 2 - rules.pickupHeight / 2;
        if (attract && Math.hypot(item.x - targetX, item.y - targetY) <= balance.magnetRange)
            item.magnetized = true;
        DropView contact = player.canPickup ? contactAlong(item, 0, 0, player.bounds) : null;
        double remaining = delta;
        while (remaining > 0 && item.y < rules.floorY) {
            if (attract && item.magnetized) {
                double dx = targetX - item.x, dy = targetY - item.y;
                double distance = Math.hypot(dx, dy);
                // Stop at the player rather than overshooting and oscillating on long frames.
                double fraction = distance == 0 ? 0
                    : Math.min(1, balance.magnetSpeed * (remaining / 1000.0) / distance);
                dx *= fraction;
                dy *= fraction;
                if (contact == null) contact = contactAlong(item, dx, dy, player.bounds);
                item.x += dx;
                item.y += dy;
                break;
            }

            if (item.directionMillis <= 0) chooseDirection(item);
            double right = rules.right - rules.pickupWidth;
            if (item.x <= rules.left) item.velocityX = Math.abs(item.velocityX);
            if (item.x >= right) item.velocityX = -Math.abs(item.velocityX);
            // A play area exactly one item wide has no horizontal room.
            double vx = right == rules.left ? 0 : item.velocityX;
            double wallMillis = vx == 0 ? Double.POSITIVE_INFINITY
                : ((vx > 0 ? right : rules.left) - item.x) / vx * 1000;
            double bottomMillis = (rules.floorY - item.y) / item.velocityY * 1000;
            double step = Math.min(remaining, Math.min(item.directionMillis, Math.min(wallMillis, bottomMillis)));
            double dx = vx * (step / 1000.0), dy = item.velocityY * (step / 1000.0);
            double entry = attract && !item.magnetized
                ? rangeEntry(item.x - targetX, item.y - targetY, dx, dy, balance.magnetRange)
                : Double.POSITIVE_INFINITY;
            boolean captured = entry <= 1;
            if (captured) {
                step *= entry;
                dx *= entry;
                dy *= entry;
            }
            if (contact == null && player.canPickup) contact = contactAlong(item, dx, dy, player.bounds);
            item.x = Math.max(rules.left, Math.min(right, item.x + dx));
            item.y += dy;
            remaining = Math.max(0, remaining - step);
            item.directionMillis = Math.max(0, item.directionMillis - step);
            if (captured) {
                item.magnetized = true;
                // Range entry may coincide with contact at the segment endpoint.
                DropView attractedContact = contactAlong(item, 0, 0, player.bounds);
                if (attractedContact != null) contact = attractedContact;
            } else {
                if (step == wallMillis) {
                    item.x = vx > 0 ? right : rules.left;
                    item.velocityX = -item.velocityX;
                }
                if (step == bottomMillis) item.y = rules.floorY;
            }
        }
        return contact;
    }

    /** First entry of a line segment into the center-based magnet circle; infinity means no entry. */
    private static double rangeEntry(double x, double y, double dx, double dy, double radius) {
        double c = x * x + y * y - radius * radius;
        if (c <= 0) return 0;
        double a = dx * dx + dy * dy;
        if (a == 0) return Double.POSITIVE_INFINITY;
        double b = x * dx + y * dy;
        if (b >= 0) return Double.POSITIVE_INFINITY;
        double discriminant = b * b - a * c;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double entry = c / (-b + Math.sqrt(discriminant));
        return entry <= 1 ? entry : Double.POSITIVE_INFINITY;
    }

    /** Sweeps the item's top-left through the player rectangle expanded by the item's size. */
    private DropView contactAlong(DroppedItem item, double dx, double dy, Bounds player) {
        double[] horizontal = axisTimes(item.x, dx, player.x - rules.pickupWidth, player.x + player.width);
        double[] vertical = axisTimes(item.y, dy, player.y - rules.pickupHeight, player.y + player.height);
        double enter = Math.max(0, Math.max(horizontal[0], vertical[0]));
        double exit = Math.min(1, Math.min(horizontal[1], vertical[1]));
        if (enter > exit) return null;
        return new DropView(item.id, item.info,
            new Bounds(item.x + dx * enter, item.y + dy * enter, rules.pickupWidth, rules.pickupHeight),
            false, item.magnetized);
    }

    private static double[] axisTimes(double position, double movement, double low, double high) {
        if (movement == 0) return position >= low && position <= high
            ? new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
            : new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double first = (low - position) / movement, last = (high - position) / movement;
        return new double[]{Math.min(first, last), Math.max(first, last)};
    }

    /**
     * Removes one contact the manager picked up successfully. Always completes within a single valid update.
     * An unknown ID is an integration bug, so it throws. Never silently fail to remove after the item was granted.
     */
    void completePickup(long dropId) {
        if (items.remove(dropId) == null)
            throw new IllegalStateException("unknown drop: " + dropId);
    }

    /** Immutable View copies in dropId order. The source drops/list are never exposed. */
    List<DropView> snapshot() {
        List<DropView> result = new ArrayList<DropView>();
        for (DroppedItem item : items.values()) result.add(item.view(rules));
        return ItemAPI.frozen(result, false);
    }

    /** Clears drops and current rules when a stage ends. Creates no expiry events and keeps the IDs. */
    void clear() {
        items.clear();
        rules = null;
    }

    private double effectiveWeight(DropRule rule, String itemId, double weight, Map<String, Integer> stacks) {
        if (weight <= 0) return 0;
        Integer count = stacks.get(itemId);
        if (count == null || count <= 0) return weight;
        ItemInfo info = definitions.find(itemId);
        if (info != null && info.maxStacks != null && count >= info.maxStacks) return 0;
        return rule.weightFor(itemId, weight, count);
    }

    /** Internal result. A plain value object with no movement/contact logic. */
    static final class Frame {
        final List<DropView> expired, contacts;
        Frame(List<DropView> expired, List<DropView> contacts) {
            this.expired = ItemAPI.frozen(expired, false);
            this.contacts = ItemAPI.frozen(contacts, false);
        }
    }
    private void requireActive() {
        if (rules == null) throw new IllegalStateException("level is not active");
    }
}
