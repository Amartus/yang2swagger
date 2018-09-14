package com.mrv.yangtools.codegen.impl.postprocessor;

import com.mrv.yangtools.common.Tuple;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class ShortenName extends ReplaceDefinitionsProcessor {

    private final String namePrefix;

    public ShortenName(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    protected Map<String, String> prepareForReplacement(Swagger swagger) {
        Map<String, Model> newKeys = new HashMap<>();
        Map<String, String> result = swagger.getDefinitions().entrySet().stream()
                .filter(e -> e.getKey().startsWith(namePrefix))
                .map(e -> {
                    String newKey = e.getKey();
                    String[] split = e.getKey().split("\\.");
                    if (split.length > 0) {
                        String tmp = namePrefix + "." + split[split.length - 1];
                        if (!swagger.getDefinitions().containsKey(tmp)) {
                            newKey = tmp;
                            newKeys.put(newKey, e.getValue());
                        }
                    }
                    return new Tuple<>(e.getKey(), newKey);
                })
                .filter(t -> !t.first().equals(t.second()))
                .collect(Collectors.toMap(Tuple::first, Tuple::second));
        swagger.getDefinitions().putAll(newKeys);
        return result;
    }
}
