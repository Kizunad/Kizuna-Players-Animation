package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.model.Vec3;

public final class JsonInput {
    private JsonInput() { }

    public static JsonObject object(JsonObject parent, String key) {
        // 必填容器在资源边界检查，避免缺字段变成无上下文的空指针。
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("需要对象字段: " + key);
        }
        return value.getAsJsonObject();
    }

    public static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        // 几何列表、索引和 UV 都使用相同的必填数组约束。
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("需要数组字段: " + key);
        }
        return value.getAsJsonArray();
    }

    public static int integer(JsonElement value) {
        number(value);
        // 不经过 float 截断，保留整个 32 位优先级范围。
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("需要 32 位整数: " + value, exception);
        }
    }

    public static float number(JsonElement value) {
        // 数学表达式和 NaN 会污染整个骨架，只在资源边界拒绝一次。
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("需要有限数值: " + value);
        }
        float result = value.getAsFloat();
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException("需要有限数值: " + value);
        }
        return result;
    }

    public static Vec3 vector(JsonElement value) {
        // 所有动画与几何向量共用同一个数值入口，不在逐帧计算里重复校验。
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("需要三维数值数组: " + value);
        }
        JsonArray array = value.getAsJsonArray();
        return new Vec3(number(array.get(0)), number(array.get(1)), number(array.get(2)));
    }

    public static String string(JsonObject object, String key) {
        // 必填字段缺失时给出字段名，资源加载器再补全文件位置。
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("需要字符串字段: " + key);
        }
        return value.getAsString();
    }

    public static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        // 不接受字符串形式的 true/false，避免输入错误被 Gson 隐式转换。
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("需要布尔字段: " + key);
        }
        return value.getAsBoolean();
    }

    public static float nonnegative(JsonObject object, String key) {
        float value = number(object.get(key));
        // 时间长度不能为负，零时长则明确表示立即切换。
        if (value < 0) {
            throw new IllegalArgumentException(key + " 不能为负数");
        }
        return value;
    }
}
