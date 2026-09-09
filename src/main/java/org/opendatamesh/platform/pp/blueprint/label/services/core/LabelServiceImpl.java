package org.opendatamesh.platform.pp.blueprint.label.services.core;

import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.label.repositories.LabelsRepository;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class LabelServiceImpl extends GenericMappedAndFilteredCrudServiceImpl<LabelSearchOptions, LabelRes, Label, String> implements LabelService {

    private static final String LABEL_NAME_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]*$";
    private static final String LABEL_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    private final LabelMapper mapper;
    private final LabelsRepository repository;

    @Autowired
    public LabelServiceImpl(LabelMapper mapper, LabelsRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    protected PagingAndSortingAndSpecificationExecutorRepository<Label, String> getRepository() {
        return repository;
    }

    @Override
    protected Specification<Label> getSpecFromFilters(LabelSearchOptions filters) {
        List<Specification<Label>> specs = new ArrayList<>();
        if (filters != null) {
            if (StringUtils.hasText(filters.getName())) {
                specs.add(LabelsRepository.Specs.hasName(filters.getName()));
            }
        }
        return SpecsUtils.combineWithAnd(specs);
    }

    @Override
    protected LabelRes toRes(Label entity) {
        return mapper.toRes(entity);
    }

    @Override
    protected Label toEntity(LabelRes resource) {
        return mapper.toEntity(resource);
    }

    @Override
    protected void validate(Label objectToValidate) {
        if (objectToValidate == null) {
            throw new BadRequestException("Label cannot be null");
        }
        validateRequired("Name", objectToValidate.getName());
        validateLength("Name", objectToValidate.getName(), 255);
        if (!objectToValidate.getName().matches(LABEL_NAME_PATTERN)) {
            throw new BadRequestException(
                    "Name may contain only simple characters (letters, digits, hyphen, underscore) and must start with a letter or digit");
        }
        if (StringUtils.hasText(objectToValidate.getColor())) {
            validateLength("Color", objectToValidate.getColor(), 32);
            if (!objectToValidate.getColor().matches(LABEL_COLOR_PATTERN)) {
                throw new BadRequestException("Color must match #RRGGBB hex format");
            }
        }
        String group = objectToValidate.getGroup();
        if (group != null) {
            group = group.trim();
        }
        if (!StringUtils.hasText(group)) {
            objectToValidate.setGroup(null);
        } else {
            validateLength("Group", group, 255);
            objectToValidate.setGroup(group);
        }
    }

    @Override
    protected void reconcile(Label objectToReconcile) {
        // no nested refs
    }

    @Override
    protected void beforeCreation(Label objectToCreate) {
        validateNaturalKeyConstraints(objectToCreate, null);
    }

    @Override
    protected void beforeOverwrite(Label objectToOverwrite) {
        validateNaturalKeyConstraints(objectToOverwrite, objectToOverwrite.getUuid());
    }

    @Override
    public LabelRes overwriteResource(String uuid, LabelRes resource) {
        resource.setUuid(uuid);
        return super.overwriteResource(uuid, resource);
    }

    private void validateNaturalKeyConstraints(Label label, String excludeUuid) {
        boolean existsByName;
        if (StringUtils.hasText(excludeUuid)) {
            existsByName = repository.existsByNameIgnoreCaseAndUuidNot(label.getName(), excludeUuid);
        } else {
            existsByName = repository.existsByNameIgnoreCase(label.getName());
        }
        if (existsByName) {
            throw new ResourceConflictException("A label with name '" + label.getName() + "' already exists");
        }
    }

    private void validateRequired(String fieldName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " is required");
        }
    }

    private void validateLength(String fieldName, String value, int maxLength) {
        if (StringUtils.hasText(value) && value.length() > maxLength) {
            throw new BadRequestException(fieldName + " cannot exceed " + maxLength + " characters");
        }
    }
}
