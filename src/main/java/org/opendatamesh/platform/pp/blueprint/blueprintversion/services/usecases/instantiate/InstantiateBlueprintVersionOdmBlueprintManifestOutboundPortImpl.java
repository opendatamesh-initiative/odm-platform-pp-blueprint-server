package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.springframework.util.StringUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

class InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl implements InstantiateBlueprintVersionManifestOutboundPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Matches manifest schema versions used in the platform (e.g. {@code v1}, {@code 1.0.0}). */
    private static final String VERSION = "(v1|1\\.\\d+\\.\\d+.*)";
    private static final LocalValidatorFactoryBean SPRING_VALIDATOR = buildValidator();

    private static LocalValidatorFactoryBean buildValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    @Override
    public void validateManifestAndParameters(String spec, String specVersion, JsonNode content, Map<String, JsonNode> parameters) {
        if (!(Manifest.SPEC_NAME.equalsIgnoreCase(spec) && specVersion.matches(VERSION))) {
            throw new UnsupportedOperationException(); //TODO
        }
        Manifest manifest = deserialize(content);
        checkForUnsupportedOperations(manifest);

        List<String> errors = new ArrayList<>();
        validateParameters(manifest, parameters == null ? Map.of() : parameters, errors);
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    private void checkForUnsupportedOperations(Manifest manifest) {
        //This will go away when implementing those
        if (manifest.getComposition() != null && !manifest.getComposition().isEmpty()) {
            throw new BadRequestException("Blueprint composition is not supported in this phase");
        }
        if (manifest.getInstantiation() == null || manifest.getInstantiation().getStrategy() == null) {
            throw new BadRequestException("Manifest instantiation.strategy is required");
        }

        if (manifest.getInstantiation().getStrategy() != ManifestInstantiation.InstantiationStrategy.MONOREPO) {
            throw new BadRequestException("Only monorepo instantiation strategy is supported in this phase");
        }
    }

    @Override
    public List<SourceRepositoryDto> retrieveAllSourceRepositories(BlueprintVersion blueprintVersion, JsonNode manifest) {
        //For now only a single source repository is supported (1-1 scenario)
        //Other source repositories are retrieved from the manifest, which contains other blueprint versions references
        //A blueprint version reference allows to: clone a repo, checkout on a specific tag

        //TODO: For multi-repo implementation, verify that git providers of pointed source blueprint
        //      have all the same git provider (base url and type)
        BlueprintRepo blueprintRepo = blueprintVersion.getBlueprint().getBlueprintRepo();
        Repository sourceRepository = new Repository();
        sourceRepository.setId(blueprintRepo.getExternalIdentifier());
        sourceRepository.setName(blueprintRepo.getName());
        sourceRepository.setDescription(blueprintRepo.getDescription());
        sourceRepository.setDefaultBranch(blueprintRepo.getDefaultBranch());
        sourceRepository.setOwnerId(blueprintRepo.getOwnerId());
        sourceRepository.setCloneUrlHttp(blueprintRepo.getRemoteUrlHttp());
        sourceRepository.setCloneUrlSsh(blueprintRepo.getRemoteUrlSsh());
        SourceRepositoryDto rootSourceRepo = new SourceRepositoryDto(null, BlueprintRepositoryLogicalType.ROOT, blueprintVersion.getTag(), sourceRepository);

        return List.of(rootSourceRepo);
    }

    @Override
    public void validateTargetRepositories(BlueprintVersion blueprintVersion, List<TargetRepositoryDto> targetRepositories) {
        // Must have exactly one target repository of type 'root'; only monorepo is supported for now
        if (targetRepositories == null || targetRepositories.size() != 1) {
            throw new BadRequestException("Exactly one target repository of type 'root' is required, only monorepo is supported in this phase");
        }
        TargetRepositoryDto target = targetRepositories.getFirst();
        if (target.type() != BlueprintRepositoryLogicalType.ROOT) {
            throw new BadRequestException("Target repository type must be 'root', only monorepo is supported in this phase");
        }
    }

    private void validateParameters(Manifest manifest, Map<String, JsonNode> parameters, List<String> errors) {
        if (manifest.getParameters() == null) {
            return;
        }
        for (ManifestParameter parameter : manifest.getParameters()) {
            JsonNode value = parameters.get(parameter.getKey());
            if (value == null) {
                if (Boolean.TRUE.equals(parameter.getRequired()) && parameter.getDefaultValue() == null) {
                    errors.add("Missing required parameter '%s'".formatted(parameter.getKey()));
                }
                continue;
            }
            validateParameterType(parameter, value, errors);
            validateParameterConstraints(parameter, value, errors);
        }
    }

    private void validateParameterType(ManifestParameter parameter, JsonNode value, List<String> errors) {
        ManifestParameter.ManifestParameterType type = parameter.getType();
        if (type == null) {
            return;
        }
        boolean isValid = switch (type) {
            case STRING -> value.isTextual();
            case INTEGER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray();
            case OBJECT -> value.isObject();
        };
        if (!isValid) {
            errors.add("Parameter '%s' must be of type %s".formatted(parameter.getKey(), type.name().toLowerCase()));
        }
    }

    private void validateParameterConstraints(ManifestParameter parameter, Object value, List<String> errors) {
        ManifestParameterValidation validation = parameter.getValidation();
        if (validation == null) {
            return;
        }

        validateAllowedValues(parameter, value, errors, validation);
        validatePattern(parameter, value, errors, validation);
        validateFormat(parameter, value, errors, validation);
        validateMinMax(parameter.getKey(), validation.getMin(), validation.getMax(), value, errors);
    }

    private static void validateFormat(ManifestParameter parameter, Object value, List<String> errors, ManifestParameterValidation validation) {
        if (!StringUtils.hasText(validation.getFormat()) || !(value instanceof String textValue)) {
            return;
        }
        String format = validation.getFormat().trim().toLowerCase();
        boolean isValid = switch (format) {
            case "email" -> SPRING_VALIDATOR.validate(new EmailFormatValue(textValue)).isEmpty();
            case "uri" -> isValidUri(textValue);
            case "uuid" -> isValidUuid(textValue);
            default -> true;
        };
        if (!isValid) {
            errors.add("Parameter '%s' does not match required format '%s'".formatted(parameter.getKey(), format));
        }
    }

    private static boolean isValidUri(String textValue) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(textValue).build().toUri();
            return StringUtils.hasText(uri.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isValidUuid(String textValue) {
        try {
            UUID.fromString(textValue);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void validatePattern(ManifestParameter parameter, Object value, List<String> errors, ManifestParameterValidation validation) {
        if (StringUtils.hasText(validation.getPattern()) && value instanceof String textValue) {
            try {
                if (!Pattern.compile(validation.getPattern()).matcher(textValue).matches()) {
                    errors.add("Parameter '%s' does not match required pattern".formatted(parameter.getKey()));
                }
            } catch (PatternSyntaxException e) {
                errors.add("Parameter '%s' has invalid pattern constraint in manifest".formatted(parameter.getKey()));
            }
        }
    }

    private static void validateAllowedValues(ManifestParameter parameter, Object value, List<String> errors, ManifestParameterValidation validation) {
        if (validation.getAllowedValues() != null && !validation.getAllowedValues().isEmpty()) {
            JsonNode valueNode = OBJECT_MAPPER.valueToTree(value);
            boolean match = validation.getAllowedValues().stream().filter(Objects::nonNull).anyMatch(valueNode::equals);
            if (!match) {
                errors.add("Parameter '%s' value is not among allowedValues".formatted(parameter.getKey()));
            }
        }
    }

    private void validateMinMax(String key, Number min, Number max, Object value, List<String> errors) {
        Double measured = switch (value) {
            case Number number -> number.doubleValue();
            case String text -> (double) text.length();
            case Collection<?> collection -> (double) collection.size();
            default -> value != null && value.getClass().isArray() ? (double) Array.getLength(value) : null;
        };
        if (measured == null) {
            return;
        }
        if (min != null && measured < min.doubleValue()) {
            errors.add("Parameter '%s' value is below min=%s".formatted(key, min));
        }
        if (max != null && measured > max.doubleValue()) {
            errors.add("Parameter '%s' value exceeds max=%s".formatted(key, max));
        }
    }

    private Manifest deserialize(JsonNode content) {
        if (content == null) {
            throw new BadRequestException("Blueprint manifest content is required");
        }
        try {
            return ManifestParserFactory.getParser().deserialize(content);
        } catch (IOException e) {
            throw new BadRequestException("Unable to parse blueprint manifest content", e);
        }
    }

    private record EmailFormatValue(@Email String value) {
    }
}
