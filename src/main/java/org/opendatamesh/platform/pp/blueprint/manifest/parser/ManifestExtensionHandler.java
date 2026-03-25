package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.manifest.extension.ManifestComponentBaseExtendedConverter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class ManifestExtensionHandler {

    private final ExtensionHandlerStatus status;
    private final List<ManifestComponentBaseExtendedConverter<ManifestComponentBase>> componentBaseExtendedConverters;
    private final ObjectMapper mapper;

    ManifestExtensionHandler(
            ExtensionHandlerStatus status,
            List<ManifestComponentBaseExtendedConverter<ManifestComponentBase>> componentBaseExtendedConverters,
            ObjectMapper mapper
    ) {
        this.status = status;
        this.componentBaseExtendedConverters = componentBaseExtendedConverters;
        this.mapper = mapper;
    }

    void handleComponentBaseExtension(
            ManifestComponentBase componentBase,
            Class<? extends ManifestComponentBase> parentClazz
    ) {
        try {
            switch (status) {
                case SERIALIZING:
                    for (var entry : componentBase.getParsedProperties().entrySet()) {
                        Optional<ManifestComponentBaseExtendedConverter<ManifestComponentBase>> converter =
                                findSupportedExtensionConverter(parentClazz, entry.getKey());
                        if (converter.isPresent()) {
                            componentBase.addAdditionalProperty(
                                    entry.getKey(),
                                    converter.get().serialize(mapper, entry.getValue())
                            );
                        } else {
                            throw new IllegalStateException(
                                    "No ManifestComponentBaseExtendedConverter has been registered on the parser that can handle this property: "
                                            + entry.getKey() + " " + mapper.writeValueAsString(entry.getValue()));
                        }
                    }
                    break;
                case DESERIALIZING:
                    Set<String> keys = new HashSet<>(componentBase.getAdditionalProperties().keySet());
                    for (String key : keys) {
                        Optional<ManifestComponentBaseExtendedConverter<ManifestComponentBase>> converter =
                                findSupportedExtensionConverter(parentClazz, key);
                        if (converter.isPresent()) {
                            ManifestComponentBase parsed = converter.get().deserialize(
                                    mapper,
                                    componentBase.getAdditionalProperties().remove(key)
                            );
                            componentBase.addParsedProperty(key, parsed);
                        }
                    }
                    break;
            }
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<ManifestComponentBaseExtendedConverter<ManifestComponentBase>> findSupportedExtensionConverter(
            Class<? extends ManifestComponentBase> parentClazz,
            String key
    ) {
        return componentBaseExtendedConverters.stream()
                .filter(e -> e.supports(key, parentClazz))
                .findFirst();
    }

    enum ExtensionHandlerStatus {
        SERIALIZING,
        DESERIALIZING
    }
}
