package dev.underdeep.client.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.underdeep.boss.BossEntity;
import dev.underdeep.boss.Move;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven, articulated boss model ({@code assets/underdeep/bosses/<id>.json}, authored in tools/assets/stage10.py).
 * Parts form a hierarchy with pivots; animation layers are combined every frame: base pose, idle sway, walk stride, and
 * the current move's pose — blended in during the wind-up (so the telegraph is visible in the body), held while the
 * move is active and eased out during recovery — plus stagger, phase-transition, intro and defeat poses. Timing comes
 * from the server's synced stage; nothing here affects combat. Stage 11 can replace the rigs without touching mechanics.
 */
public final class BossModel extends SinglePartEntityModel<BossEntity> {
    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Map<String, float[]> base = new HashMap<>();
    private final List<Osc> idle = new ArrayList<>(), walk = new ArrayList<>();
    private final Map<String, Map<String, float[]>> poses = new HashMap<>();
    final List<String> broken = new ArrayList<>();
    final Map<Integer, List<String>> flagGlow = new HashMap<>();
    final int hideFlag;
    private final Map<String, float[]> current = new HashMap<>();

    private record Osc(String part, int axis, float amp, float speed, float phase) {
    }

    public static TexturedModelData data(JsonObject spec) {
        ModelData md = new ModelData();
        Map<String, ModelPartData> made = new HashMap<>();
        Map<String, float[]> pivots = new HashMap<>();
        made.put("root", md.getRoot());
        JsonArray arr = spec.getAsJsonArray("parts");
        boolean progress = true;
        List<JsonObject> pending = new ArrayList<>();
        arr.forEach(e -> pending.add(e.getAsJsonObject()));
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (var it = pending.iterator(); it.hasNext(); ) {
                JsonObject o = it.next();
                String parent = o.get("parent").getAsString();
                ModelPartData pd = made.get(parent);
                if (pd == null) {
                    continue;
                }
                ModelPartBuilder b = ModelPartBuilder.create();
                for (JsonElement be : o.getAsJsonArray("boxes")) {
                    JsonArray x = be.getAsJsonArray();
                    float w = x.get(3).getAsFloat(), h = x.get(4).getAsFloat(), d = x.get(5).getAsFloat();
                    b.uv(x.get(6).getAsInt(), x.get(7).getAsInt()).cuboid(x.get(0).getAsFloat(), -(x.get(1).getAsFloat() + h), x.get(2).getAsFloat(), w, h, d,
                            Dilation.NONE);
                }
                JsonArray pv = o.getAsJsonArray("pivot");
                JsonArray rot = o.getAsJsonArray("rot");
                float px = pv.get(0).getAsFloat(), py = pv.get(1).getAsFloat(), pz = pv.get(2).getAsFloat();
                float y = parent.equals("root") ? 24 - py : -py;
                made.put(o.get("name").getAsString(), pd.addChild(o.get("name").getAsString(), b,
                        ModelTransform.of(px, y, pz, rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat())));
                pivots.put(o.get("name").getAsString(), new float[]{px, py, pz});
                it.remove();
                progress = true;
            }
        }
        JsonArray tex = spec.getAsJsonArray("texture");
        return TexturedModelData.of(md, tex.get(0).getAsInt(), tex.get(1).getAsInt());
    }

    public BossModel(ModelPart root, JsonObject spec) {
        this.root = root;
        for (JsonElement e : spec.getAsJsonArray("parts")) {
            JsonObject o = e.getAsJsonObject();
            String n = o.get("name").getAsString();
            ModelPart p = find(root, n);
            if (p != null) {
                parts.put(n, p);
                JsonArray rot = o.getAsJsonArray("rot");
                base.put(n, new float[]{rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat()});
            }
        }
        for (String k : new String[]{"idle", "walk"}) {
            if (spec.has(k)) {
                for (JsonElement e : spec.getAsJsonArray(k)) {
                    JsonObject o = e.getAsJsonObject();
                    (k.equals("idle") ? idle : walk).add(new Osc(o.get("part").getAsString(), o.get("axis").getAsInt(), o.get("amp").getAsFloat(),
                            o.has("speed") ? o.get("speed").getAsFloat() : 0, o.get("phase").getAsFloat()));
                }
            }
        }
        if (spec.has("poses")) {
            for (var e : spec.getAsJsonObject("poses").entrySet()) {
                Map<String, float[]> m = new HashMap<>();
                for (var pe : e.getValue().getAsJsonObject().entrySet()) {
                    JsonArray a = pe.getValue().getAsJsonArray();
                    m.put(pe.getKey(), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
                }
                poses.put(e.getKey(), m);
            }
        }
        if (spec.has("brokenParts")) {
            spec.getAsJsonArray("brokenParts").forEach(e -> broken.add(e.getAsString()));
        }
        if (spec.has("flagGlow")) {
            for (var e : spec.getAsJsonObject("flagGlow").entrySet()) {
                List<String> l = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                flagGlow.put(Integer.parseInt(e.getKey()), l);
            }
        }
        hideFlag = spec.has("hideFlag") ? spec.get("hideFlag").getAsInt() : -1;
    }

    private static ModelPart find(ModelPart node, String name) {
        if (node.hasChild(name)) {
            return node.getChild(name);
        }
        for (var c : childrenOf(node)) {
            ModelPart f = find(c, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    private static List<ModelPart> childrenOf(ModelPart p) {
        List<ModelPart> l = new ArrayList<>();
        p.traverse().forEach(c -> {
            if (c != p) {
                l.add(c);
            }
        });
        return l;
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    public ModelPart part(String n) {
        return parts.get(n);
    }

    @Override
    public void setAngles(BossEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        for (var en : parts.entrySet()) {
            float[] b = base.get(en.getKey());
            en.getValue().pitch = b[0];
            en.getValue().yaw = b[1];
            en.getValue().roll = b[2];
            en.getValue().visible = true;
        }
        for (Osc o : idle) {
            add(o.part, o.axis, o.amp * MathHelper.sin(age * o.speed * 6.28f + o.phase));
        }
        float stride = Math.min(1, limbDistance * 1.6f);
        for (Osc o : walk) {
            add(o.part, o.axis, o.amp * stride * MathHelper.sin(limbAngle * 0.55f + o.phase));
        }
        byte stage = e.stage();
        float len = Math.max(1, e.stageLength());
        float t = MathHelper.clamp((e.clientStageAge + (age - (int) age)) / len, 0, 1);
        String move = moveName(e);
        switch (stage) {
            case BossEntity.WINDUP -> blend(move + ":windup", smooth(t));
            case BossEntity.ACTIVE -> {
                blend(move + ":windup", 1 - smooth(Math.min(1, t * 3)));
                blend(move + ":active", smooth(Math.min(1, t * 3)));
            }
            case BossEntity.RECOVER -> {
                if (poses.containsKey(move + ":recover")) {
                    blend(move + ":recover", 1 - t);
                } else {
                    blend(move + ":active", 1 - smooth(t));
                }
            }
            case BossEntity.STAGGER -> blend("stagger", smooth(Math.min(1, t * 4)) * (1 - Math.max(0, t - 0.85f) / 0.15f));
            case BossEntity.INTRO -> blend("intro", MathHelper.sin(t * MathHelper.PI));
            case BossEntity.TRANSITION -> blend(poses.containsKey("transition") ? "transition" : "intro", MathHelper.sin(t * MathHelper.PI));
            case BossEntity.DEFEAT -> blend("defeat", smooth(t));
            default -> {
            }
        }
        for (int i = 0; i < broken.size(); i++) {
            if (e.flag(i)) {
                ModelPart p = parts.get(broken.get(i));
                if (p != null) {
                    p.visible = false;
                }
            }
        }
    }

    static String moveName(BossEntity e) {
        int id = e.moveId();
        var moves = e.brain == null ? null : e.brain;
        if (id <= 0 || moves == null) {
            return "";
        }
        Move m = e.brain.move(id);
        return m == null ? "" : m.name();
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private void blend(String key, float w) {
        Map<String, float[]> p = poses.get(key);
        if (p == null || w <= 0) {
            return;
        }
        for (var en : p.entrySet()) {
            float[] v = en.getValue();
            add(en.getKey(), 0, v[0] * w);
            add(en.getKey(), 1, v[1] * w);
            add(en.getKey(), 2, v[2] * w);
        }
    }

    private void add(String part, int axis, float v) {
        ModelPart p = parts.get(part);
        if (p == null) {
            return;
        }
        switch (axis) {
            case 0 -> p.pitch += v;
            case 1 -> p.yaw += v;
            default -> p.roll += v;
        }
    }
}
