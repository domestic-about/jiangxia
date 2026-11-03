package org.dromara.lqg.cryo.batch.domain.bo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/** 防止 Jackson 把小数天数截断成整数；空串与 null 都表示清空。 */
public class PassageCycleDaysDeserializer extends StdDeserializer<Integer> {

    public PassageCycleDaysDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return parser.getIntValue();
        }
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String value = parser.getText().trim();
            if (value.isEmpty()) {
                return null;
            }
            if (value.matches("[0-9]+")) {
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException ignored) {
                    // 超出整数范围与其它非法输入一样拒绝。
                }
            }
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
}
