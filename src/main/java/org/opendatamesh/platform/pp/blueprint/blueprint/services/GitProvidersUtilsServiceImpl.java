package org.opendatamesh.platform.pp.blueprint.blueprint.services;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.model.Branch;
import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.CommitListFilter;
import org.opendatamesh.platform.git.model.CommitListNoFilter;
import org.opendatamesh.platform.git.model.CommitListRangeFilter;
import org.opendatamesh.platform.git.model.CommitListSingleBranchFilter;
import org.opendatamesh.platform.git.model.CommitRef;
import org.opendatamesh.platform.git.model.CommitRefBranch;
import org.opendatamesh.platform.git.model.CommitRefHash;
import org.opendatamesh.platform.git.model.CommitRefTag;
import org.opendatamesh.platform.git.model.Tag;
import org.opendatamesh.platform.git.model.Organization;
import org.opendatamesh.platform.git.model.ProviderCustomResource;
import org.opendatamesh.platform.git.model.ProviderCustomResourceDefinition;
import org.opendatamesh.platform.git.model.ProviderCustomResourceProperty;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryOwnerType;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
import org.opendatamesh.platform.git.model.RepositoryVisibility;
import org.opendatamesh.platform.git.model.User;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderExtension;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.git.provider.GitProviderModelResourceType;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.BranchMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.BranchRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.CommitMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.CommitRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.CommitSearchOptions;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.CreateRepositoryReqRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.CreateRepositoryTagReqRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.OrganizationMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.OrganizationRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.ProviderCustomResourceDefinitionMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.ProviderCustomResourceDefinitionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.ProviderCustomResourceMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.ProviderCustomResourceRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.ProviderCustomResourcesDefinitionsRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.ProviderIdentifierRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.TagMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.TagRes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

@Service
public class GitProvidersUtilsServiceImpl implements GitProvidersUtilsService {

    private final OrganizationMapper organizationMapper;
    private final RepositoryMapper repositoryMapper;
    private final ProviderCustomResourceDefinitionMapper providerCustomResourceDefinitionMapper;
    private final ProviderCustomResourceMapper providerCustomResourceMapper;
    private final BranchMapper branchMapper;
    private final TagMapper tagMapper;
    private final CommitMapper commitMapper;

    private final GitProviderFactory gitProviderFactory;

    public GitProvidersUtilsServiceImpl(OrganizationMapper organizationMapper, RepositoryMapper repositoryMapper, ProviderCustomResourceDefinitionMapper providerCustomResourceDefinitionMapper, ProviderCustomResourceMapper providerCustomResourceMapper, BranchMapper branchMapper, TagMapper tagMapper, CommitMapper commitMapper, GitProviderFactory gitProviderFactory) {
        this.organizationMapper = organizationMapper;
        this.repositoryMapper = repositoryMapper;
        this.providerCustomResourceDefinitionMapper = providerCustomResourceDefinitionMapper;
        this.providerCustomResourceMapper = providerCustomResourceMapper;
        this.branchMapper = branchMapper;
        this.tagMapper = tagMapper;
        this.commitMapper = commitMapper;
        this.gitProviderFactory = gitProviderFactory;
    }

    @Override
    public Page<OrganizationRes> listOrganizations(ProviderIdentifierRes providerIdentifier, HttpHeaders headers, Pageable pageable) {
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        Page<Organization> organizations = provider.listOrganizations(pageable);

        return organizations.map(organizationMapper::toRes);
    }

    @Override
    public Page<RepositoryRes> listRepositories(ProviderIdentifierRes providerIdentifier, boolean showUserRepositories, OrganizationRes organizationRes, MultiValueMap<String, String> parameters, HttpHeaders headers, Pageable pageable) {
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        // Validate: if showUserRepositories is false, organizationRes cannot be null
        if (!showUserRepositories && organizationRes == null) {
            throw new BadRequestException("Organization information is required when showUserRepositories is false");
        }

        Organization org = null;
        User user = null;

        if (organizationRes != null) {
            // Use organization for listing repositories
            org = organizationMapper.toEntity(organizationRes);
            Page<Repository> repositories = provider.listRepositories(org, null, parameters, pageable);
            return repositories.map(repositoryMapper::toRes);
        } else {
            // organizationRes is null, so showUserRepositories must be true
            // Fetch current user information
            user = provider.getCurrentUser();
            Page<Repository> repositories = provider.listRepositories(null, user, parameters, pageable);
            return repositories.map(repositoryMapper::toRes);
        }
    }

    @Override
    public RepositoryRes createRepository(ProviderIdentifierRes providerIdentifier, OrganizationRes organizationRes, HttpHeaders headers, CreateRepositoryReqRes createRepositoryReqRes) {
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        validateCreateRepositoryReqRes(createRepositoryReqRes);

        Repository repositoryToCreate;
        if (organizationRes != null) {
            // Use organization to create repository
            Organization org = organizationMapper.toEntity(organizationRes);
            repositoryToCreate = buildRepositoryObject(createRepositoryReqRes, null, org);
        } else {
            // organizationRes is null, fetch current user information
            User user = provider.getCurrentUser();
            repositoryToCreate = buildRepositoryObject(createRepositoryReqRes, user, null);
        }

        Repository createdRepository = provider.createRepository(repositoryToCreate);

        return repositoryMapper.toRes(createdRepository);
    }

    @Override
    public ProviderCustomResourcesDefinitionsRes getProviderCustomResourcesDefinitions(ProviderIdentifierRes providerIdentifier, String resourceType) {
        GitProviderModelResourceType modelResourceType;
        try {
            modelResourceType = GitProviderModelResourceType.valueOf(resourceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported resource type: " + resourceType);
        }
        GitProviderExtension providerExtension = gitProviderFactory.buildGitProviderExtension(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl())
        );
        List<ProviderCustomResourceDefinition> definitions = providerExtension
                .getProviderCustomResourceDefinitions(modelResourceType);
        List<ProviderCustomResourceDefinitionRes> definitionResList = definitions.stream()
                .map(providerCustomResourceDefinitionMapper::toRes)
                .toList();

        return new ProviderCustomResourcesDefinitionsRes(definitionResList);
    }

    @Override
    public Page<ProviderCustomResourceRes> getProviderCustomResources(ProviderIdentifierRes providerIdentifier, String customResourceType, MultiValueMap<String, String> parameters, HttpHeaders headers, Pageable pageable) {
        if (!StringUtils.hasText(customResourceType)) {
            throw new BadRequestException("Custom resource type is required");
        }
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );
        Page<ProviderCustomResource> customResources = provider.getProviderCustomResources(customResourceType, parameters, pageable);
        return customResources.map(providerCustomResourceMapper::toRes);
    }

    @Override
    public Page<BranchRes> listBranches(ProviderIdentifierRes providerIdentifier, String repositoryId, String ownerId, HttpHeaders headers, Pageable pageable) {
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        // Get repository information first
        Optional<Repository> repositoryOpt = provider.getRepository(repositoryId, ownerId);
        if (repositoryOpt.isEmpty()) {
            throw new BadRequestException("Repository not found with ID: " + repositoryId);
        }

        Repository repository = repositoryOpt.get();

        // Call the Git provider to list branches
        Page<Branch> branches = provider.listBranches(repository, pageable);

        // Map to DTOs
        return branches.map(branchMapper::toRes);
    }

    @Override
    public Page<TagRes> listTags(ProviderIdentifierRes providerIdentifier, String repositoryId, String ownerId, HttpHeaders headers, Pageable pageable) {
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        Optional<Repository> repositoryOpt = provider.getRepository(repositoryId, ownerId);
        if (repositoryOpt.isEmpty()) {
            throw new BadRequestException("Repository not found with ID: " + repositoryId);
        }

        Repository repository = repositoryOpt.get();

        Page<Tag> tags = provider.listTags(repository, pageable);

        return tags.map(tagMapper::toRes);
    }

    @Override
    public Page<CommitRes> listCommits(ProviderIdentifierRes providerIdentifier, String repositoryId, String ownerId, CommitSearchOptions searchOptions, HttpHeaders headers, Pageable pageable) {
        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        Repository repository = provider.getRepository(repositoryId, ownerId)
                .orElseThrow(() -> new BadRequestException("Repository not found with ID: " + repositoryId));

        CommitListFilter filter = buildCommitListFilter(searchOptions, repository.getDefaultBranch());
        Page<Commit> commits = provider.listCommits(repository, filter, pageable);

        return commits.map(commitMapper::toRes);
    }

    @Override
    public void createRepositoryTag(ProviderIdentifierRes providerIdentifier, String repositoryId, String ownerId, HttpHeaders headers, CreateRepositoryTagReqRes createRepositoryTagReqRes) {
        validateCreateRepositoryTagReqRes(createRepositoryTagReqRes);

        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(providerIdentifier.getProviderType(), providerIdentifier.getProviderBaseUrl()),
                headers
        );

        Repository gitRepo = provider.getRepository(repositoryId, ownerId)
                .orElseThrow(() -> new BadRequestException("Repository not found with ID: " + repositoryId));

        String defaultBranch = StringUtils.hasText(gitRepo.getDefaultBranch()) ? gitRepo.getDefaultBranch() : null;
        String branchName = StringUtils.hasText(createRepositoryTagReqRes.getBranchName())
                ? createRepositoryTagReqRes.getBranchName()
                : defaultBranch;
        if (!StringUtils.hasText(branchName)) {
            throw new BadRequestException(
                    "Either branchName or a repository default branch is required to locate the clone state");
        }
        RepositoryPointerBranch repositoryPointer = new RepositoryPointerBranch(branchName);

        try {
            provider.gitOperation().readRepository(gitRepo, repositoryPointer, repository -> {
                String targetSha = retrieveTagTargetCommit(createRepositoryTagReqRes, repository, provider, defaultBranch);
                Tag newTag = new Tag();
                newTag.setName(createRepositoryTagReqRes.getName());
                newTag.setCommitHash(targetSha);
                if (StringUtils.hasText(createRepositoryTagReqRes.getMessage())) {
                    newTag.setMessage(createRepositoryTagReqRes.getMessage());
                }
                newTag.setAuthor(createRepositoryTagReqRes.getAuthorName());
                newTag.setAuthorEmail(createRepositoryTagReqRes.getAuthorEmail());
                provider.gitOperation().addTag(repository, newTag);
                provider.gitOperation().push(repository, true);
            });
        } catch (GitOperationException e) {
            throw new BadRequestException("Failed to create tag on repository " + repositoryId + " with owner ID " + ownerId + ": " + e.getMessage(), e);
        }
    }

    private void validateCreateRepositoryTagReqRes(CreateRepositoryTagReqRes req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BadRequestException("Tag name is required");
        }
    }

    private String retrieveTagTargetCommit(CreateRepositoryTagReqRes req, File repository, GitProvider provider, String defaultBranch) {
        if (StringUtils.hasText(req.getCommitHash())) {
            return req.getCommitHash();
        }
        if (StringUtils.hasText(req.getBranchName())) {
            return provider.gitOperation().getHeadSha(repository, req.getBranchName());
        }
        return provider.gitOperation().getHeadSha(repository, defaultBranch);
    }

    /**
     * Same rules as the registry repository commits endpoint: {@code branchName} alone lists one
     * branch; otherwise at most one {@code from} and one {@code to} ref, a missing side falling
     * back to the repository default branch.
     */
    private CommitListFilter buildCommitListFilter(CommitSearchOptions options, String defaultBranchName) {
        if (options == null) {
            return CommitListNoFilter.getInstance();
        }

        boolean hasBranchName = StringUtils.hasText(options.getBranchName());
        boolean hasFromBranchName = StringUtils.hasText(options.getFromBranchName());
        boolean hasToBranchName = StringUtils.hasText(options.getToBranchName());
        boolean hasFromTag = StringUtils.hasText(options.getFromTagName());
        boolean hasToTag = StringUtils.hasText(options.getToTagName());
        boolean hasFromCommit = StringUtils.hasText(options.getFromCommitHash());
        boolean hasToCommit = StringUtils.hasText(options.getToCommitHash());

        if (hasBranchName && (hasFromBranchName || hasToBranchName || hasFromTag || hasToTag || hasFromCommit || hasToCommit)) {
            throw new BadRequestException(
                    "'branchName' cannot be used together with 'fromBranchName' or 'toBranchName' or from/to tag/commit. " +
                            "Use either branchName alone to list commits on one branch, or from/to parameters to list commits between refs.");
        }

        int fromCount = (hasFromTag ? 1 : 0) + (hasFromCommit ? 1 : 0) + (hasFromBranchName ? 1 : 0);
        int toCount = (hasToTag ? 1 : 0) + (hasToCommit ? 1 : 0) + (hasToBranchName ? 1 : 0);
        if (fromCount > 1 || toCount > 1) {
            throw new BadRequestException("Set at most one 'from' and one 'to' parameter (tag, commit hash or branch)");
        }

        if (hasBranchName) {
            return new CommitListSingleBranchFilter(new CommitRefBranch(options.getBranchName()));
        }

        CommitRef fromRef = buildFromRef(options);
        CommitRef toRef = buildToRef(options);

        if (fromRef == null && toRef == null) {
            return CommitListNoFilter.getInstance();
        }
        if (fromRef == null || toRef == null) {
            if (!StringUtils.hasText(defaultBranchName)) {
                throw new BadRequestException("For commit range filter both 'from' and 'to' parameters are required, or configure a default branch.");
            }
            if (fromRef == null) {
                fromRef = new CommitRefBranch(defaultBranchName);
            } else {
                toRef = new CommitRefBranch(defaultBranchName);
            }
        }

        return new CommitListRangeFilter(fromRef, toRef);
    }

    private CommitRef buildFromRef(CommitSearchOptions options) {
        if (StringUtils.hasText(options.getFromTagName())) {
            return new CommitRefTag(options.getFromTagName());
        }
        if (StringUtils.hasText(options.getFromCommitHash())) {
            return new CommitRefHash(options.getFromCommitHash());
        }
        if (StringUtils.hasText(options.getFromBranchName())) {
            return new CommitRefBranch(options.getFromBranchName());
        }
        return null;
    }

    private CommitRef buildToRef(CommitSearchOptions options) {
        if (StringUtils.hasText(options.getToTagName())) {
            return new CommitRefTag(options.getToTagName());
        }
        if (StringUtils.hasText(options.getToCommitHash())) {
            return new CommitRefHash(options.getToCommitHash());
        }
        if (StringUtils.hasText(options.getToBranchName())) {
            return new CommitRefBranch(options.getToBranchName());
        }
        return null;
    }

    /**
     * Validates the CreateRepositoryReqRes object
     */
    private void validateCreateRepositoryReqRes(CreateRepositoryReqRes createRepositoryReqRes) {
        if (!StringUtils.hasText(createRepositoryReqRes.getName())) {
            throw new BadRequestException("Repository name is required and cannot be empty");
        }
        if (createRepositoryReqRes.getIsPrivate() == null) {
            throw new BadRequestException("Repository visibility (isPrivate) is required and cannot be null");
        }
    }

    /**
     * Creates a Repository domain object from the request and user/organization information
     */
    private Repository buildRepositoryObject(CreateRepositoryReqRes createRepositoryReqRes, User user, Organization org) {
        Repository repositoryToCreate = new Repository();
        repositoryToCreate.setName(createRepositoryReqRes.getName());
        repositoryToCreate.setDescription(createRepositoryReqRes.getDescription());
        repositoryToCreate.setVisibility(createRepositoryReqRes.getIsPrivate() ?
                RepositoryVisibility.PRIVATE : RepositoryVisibility.PUBLIC);

        // Set owner information
        if (org != null) {
            repositoryToCreate.setOwnerType(RepositoryOwnerType.ORGANIZATION);
            repositoryToCreate.setOwnerId(org.getId());
        } else {
            repositoryToCreate.setOwnerType(RepositoryOwnerType.ACCOUNT);
            repositoryToCreate.setOwnerId(user.getId());
        }

        // Map providerCustomResourceProperties
        if (createRepositoryReqRes.getProviderCustomResourceProperties() != null) {
            List<ProviderCustomResourceProperty> properties = createRepositoryReqRes.getProviderCustomResourceProperties().stream()
                    .map(propRes -> {
                        ProviderCustomResourceProperty prop = new ProviderCustomResourceProperty();
                        prop.setName(propRes.getName());
                        prop.setValue(propRes.getValue());
                        return prop;
                    })
                    .toList();
            repositoryToCreate.setProviderCustomResourceProperties(properties);
        }

        return repositoryToCreate;
    }
}
