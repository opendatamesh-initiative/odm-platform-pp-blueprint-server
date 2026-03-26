package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories.BlueprintVersionsShortRepository;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionShortRes;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link BlueprintVersionQueryService} for querying multiple BlueprintVersion entities.
 * <p>
 * This service implementation is optimized for read operations involving multiple entities,
 * using the lightweight BlueprintVersionShort entity to exclude manifest and descriptor template content for better performance.
 */

@Service
public class BlueprintVersionQueryServiceImpl implements BlueprintVersionQueryService {

    private final BlueprintVersionMapper mapper;
    private final BlueprintVersionsShortRepository repository;

    @Autowired
    public BlueprintVersionQueryServiceImpl(BlueprintVersionMapper mapper, BlueprintVersionsShortRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    public BlueprintVersionShort findOne(String uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Resource with id=" + uuid + " not found"));
    }

    @Override
    public Page<BlueprintVersionShortRes> findAllResourcesShort(Pageable pageable, BlueprintVersionSearchOptions searchOptions) {
        return findAllShort(pageable, searchOptions).map(mapper::toShortResFromShort);
    }

    @Override
    public Page<BlueprintVersionShort> findAllShort(Pageable pageable, BlueprintVersionSearchOptions searchOptions) {
        Specification<BlueprintVersionShort> spec = getSpecFromFilters(searchOptions);
        return repository.findAll(spec, pageable);
    }

    private Specification<BlueprintVersionShort> getSpecFromFilters(BlueprintVersionSearchOptions searchOptions) {
        List<Specification<BlueprintVersionShort>> specs = new ArrayList<>();
        if (searchOptions != null) {
            if (StringUtils.hasText(searchOptions.getBlueprintUuid())) {
                specs.add(BlueprintVersionsShortRepository.Specs.hasBlueprintUuid(searchOptions.getBlueprintUuid()));
            }
            if (StringUtils.hasText(searchOptions.getName())) {
                specs.add(BlueprintVersionsShortRepository.Specs.hasName(searchOptions.getName()));
            }
            if (StringUtils.hasText(searchOptions.getTag())) {
                specs.add(BlueprintVersionsShortRepository.Specs.hasTag(searchOptions.getTag()));
            }
            if (StringUtils.hasText(searchOptions.getVersionNumber())) {
                specs.add(BlueprintVersionsShortRepository.Specs.hasVersionNumber(searchOptions.getVersionNumber()));
            }
            if (searchOptions.getSearch() != null) {
                specs.add(BlueprintVersionsShortRepository.Specs.matchSearch(searchOptions.getSearch()));
            }
        }
        return SpecsUtils.combineWithAnd(specs);
    }
}
