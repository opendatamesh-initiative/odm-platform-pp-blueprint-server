package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.git.model.*;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderExtension;
import org.opendatamesh.platform.git.provider.GitProviderModelResourceType;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opendatamesh.platform.git.git.GitOperation;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

public class GitProviderControllerIT extends BlueprintApplicationIT {

    private static final String TEST_PAT_TOKEN = "test-pat-token";
    private static final String TEST_PAT_USERNAME = "test-user";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private GitProviderFactoryMock gitProviderFactoryMock;

    @AfterEach
    void tearDown() {
        // Reset the test factory mock
        gitProviderFactoryMock.reset();
    }

    @Test
    public void whenGetOrganizationsWithValidProviderThenReturnOrganizations() throws Exception {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Organization mockOrg1 = createMockOrganization("123", "test-org-1");
        Organization mockOrg2 = createMockOrganization("456", "test-org-2");
        List<Organization> mockOrganizations = Arrays.asList(mockOrg1, mockOrg2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Organization> mockPage = new PageImpl<>(mockOrganizations, pageable, 2);

        // Configure the mock GitProvider to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.listOrganizations(any(Pageable.class))).thenReturn(mockPage);

        // Create expected response objects
        OrganizationRes expectedOrg1 = new OrganizationRes("123", "test-org-1", "https://github.com/test-org-1");
        OrganizationRes expectedOrg2 = new OrganizationRes("456", "test-org-2", "https://github.com/test-org-2");

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/organizations?providerType=GITHUB&providerBaseUrl=https://api.github.com&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Verify response structure
        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("content")).isTrue();
        assertThat(responseBody.has("totalElements")).isTrue();
        assertThat(responseBody.get("totalElements").asInt()).isEqualTo(2);

        // Verify content array
        JsonNode content = responseBody.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(2);

        // Parse and verify first organization
        OrganizationRes actualOrg1 = objectMapper.treeToValue(content.get(0), OrganizationRes.class);
        assertThat(actualOrg1).usingRecursiveComparison().isEqualTo(expectedOrg1);

        // Parse and verify second organization
        OrganizationRes actualOrg2 = objectMapper.treeToValue(content.get(1), OrganizationRes.class);
        assertThat(actualOrg2).usingRecursiveComparison().isEqualTo(expectedOrg2);
    }

    @Test
    public void whenGetOrganizationsWithoutProviderTypeThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // When - missing required providerType parameter
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/organizations?page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then - validation should catch missing providerType at controller level
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetOrganizationsWithoutProviderBaseUrlThenReturnBadRequest() {
        // Given - providerType present but providerBaseUrl missing
        HttpHeaders headers = createTestHeaders();

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/organizations?providerType=GITHUB&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetRepositoriesWithoutProviderBaseUrlThenReturnBadRequest() {
        // Given - providerType present but providerBaseUrl missing
        HttpHeaders headers = createTestHeaders();

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&showUserRepositories=true&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetRepositoriesWithValidParametersThenReturnRepositories() throws Exception {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Repository mockRepo1 = createMockRepository("repo1", "Test Repository 1");
        Repository mockRepo2 = createMockRepository("repo2", "Test Repository 2");
        List<Repository> mockRepositories = Arrays.asList(mockRepo1, mockRepo2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Repository> mockPage = new PageImpl<>(mockRepositories, pageable, 2);

        // Mock the GitProvider method to return our test data - use any() for all parameters
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        User mockUser = new User();
        mockUser.setId("123");
        mockUser.setUsername("testuser");
        when(mockGitProvider.getCurrentUser()).thenReturn(mockUser);
        when(mockGitProvider.listRepositories(any(), any(), any(), any())).thenReturn(mockPage);

        // Create expected response objects
        RepositoryRes expectedRepo1 = new RepositoryRes("123456", "repo1", "Test Repository 1",
                "https://github.com/test/repo1.git", "git@github.com:test/repo1.git", "main",
                null, null, null);
        RepositoryRes expectedRepo2 = new RepositoryRes("123456", "repo2", "Test Repository 2",
                "https://github.com/test/repo2.git", "git@github.com:test/repo2.git", "main",
                null, null, null);

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com&showUserRepositories=true&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Verify response structure
        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("content")).isTrue();
        assertThat(responseBody.has("totalElements")).isTrue();
        assertThat(responseBody.get("totalElements").asInt()).isEqualTo(2);

        // Verify content array
        JsonNode content = responseBody.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(2);

        // Parse and verify first repository
        RepositoryRes actualRepo1 = objectMapper.treeToValue(content.get(0), RepositoryRes.class);
        assertThat(actualRepo1).usingRecursiveComparison().isEqualTo(expectedRepo1);

        // Parse and verify second repository
        RepositoryRes actualRepo2 = objectMapper.treeToValue(content.get(1), RepositoryRes.class);
        assertThat(actualRepo2).usingRecursiveComparison().isEqualTo(expectedRepo2);
    }

    @Test
    public void whenGetRepositoriesWithOrganizationParametersThenReturnRepositories() throws Exception {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Repository mockRepo1 = createMockRepository("org-repo-1", "Organization repository 1");
        Repository mockRepo2 = createMockRepository("org-repo-2", "Organization repository 2");
        List<Repository> mockRepositories = Arrays.asList(mockRepo1, mockRepo2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Repository> mockPage = new PageImpl<>(mockRepositories, pageable, 2);

        // Mock the GitProvider method to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.listRepositories(any(), any(), any(), any())).thenReturn(mockPage);

        // Create expected response objects
        RepositoryRes expectedRepo1 = new RepositoryRes("123456", "org-repo-1", "Organization repository 1",
                "https://github.com/test/org-repo-1.git", "git@github.com:test/org-repo-1.git", "main",
                null, null, null);
        RepositoryRes expectedRepo2 = new RepositoryRes("123456", "org-repo-2", "Organization repository 2",
                "https://github.com/test/org-repo-2.git", "git@github.com:test/org-repo-2.git", "main",
                null, null, null);

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com&organizationId=456&organizationName=testorg&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Verify response structure
        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("content")).isTrue();
        assertThat(responseBody.has("totalElements")).isTrue();
        assertThat(responseBody.get("totalElements").asInt()).isEqualTo(2);

        // Verify content array
        JsonNode content = responseBody.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(2);

        // Parse and verify first repository
        RepositoryRes actualRepo1 = objectMapper.treeToValue(content.get(0), RepositoryRes.class);
        assertThat(actualRepo1).usingRecursiveComparison().isEqualTo(expectedRepo1);

        // Parse and verify second repository
        RepositoryRes actualRepo2 = objectMapper.treeToValue(content.get(1), RepositoryRes.class);
        assertThat(actualRepo2).usingRecursiveComparison().isEqualTo(expectedRepo2);
    }

    @Test
    public void whenGetRepositoriesWithoutRequiredParametersThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // When - missing organization ID and getFromCurrentUser is false
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then - validation should catch missing userId and username at controller level
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetRepositoriesWithPaginationThenReturnPaginatedResults() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Repository mockRepo1 = createMockRepository("repo1", "Test Repository 1");
        Repository mockRepo2 = createMockRepository("repo2", "Test Repository 2");
        List<Repository> mockRepositories = Arrays.asList(mockRepo1, mockRepo2);
        Pageable pageable = PageRequest.of(0, 5);
        Page<Repository> mockPage = new PageImpl<>(mockRepositories, pageable, 2);

        // Mock the GitProvider method to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.listRepositories(any(), any(), any(), any())).thenReturn(mockPage);
        when(mockGitProvider.getCurrentUser()).thenReturn(new User("123", "testuser", null, null, null));

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com&showUserRepositories=true&page=0&size=5"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify pagination structure is present in response
        assertThat(response.getBody()).contains("pageable");
        assertThat(response.getBody()).contains("totalElements");
    }

    @Test
    public void whenGetOrganizationsWithPaginationThenReturnPaginatedResults() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Organization mockOrg1 = createMockOrganization("123", "org-1");
        Organization mockOrg2 = createMockOrganization("456", "org-2");
        List<Organization> mockOrganizations = Arrays.asList(mockOrg1, mockOrg2);
        Pageable pageable = PageRequest.of(0, 5);
        Page<Organization> mockPage = new PageImpl<>(mockOrganizations, pageable, 2);

        // Mock the GitProvider method to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.listOrganizations(any(Pageable.class))).thenReturn(mockPage);

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/organizations?providerType=GITHUB&providerBaseUrl=https://api.github.com&page=0&size=5"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify pagination structure is present in response
        assertThat(response.getBody()).contains("pageable");
        assertThat(response.getBody()).contains("totalElements");
    }

    @Test
    public void whenGetOrganizationsWithSortingThenReturnSortedResults() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Organization mockOrg1 = createMockOrganization("123", "sorted-org-1");
        Organization mockOrg2 = createMockOrganization("456", "sorted-org-2");
        List<Organization> mockOrganizations = Arrays.asList(mockOrg1, mockOrg2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Organization> mockPage = new PageImpl<>(mockOrganizations, pageable, 2);

        // Mock the GitProvider method to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.listOrganizations(any(Pageable.class))).thenReturn(mockPage);

        // When - sort by name ascending
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/organizations?providerType=GITHUB&providerBaseUrl=https://api.github.com&page=0&size=10&sort=name,asc"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    public void whenGetRepositoriesWithSortingThenReturnSortedResults() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Repository mockRepo1 = createMockRepository("sorted-repo-1", "Sorted Repository 1");
        Repository mockRepo2 = createMockRepository("sorted-repo-2", "Sorted Repository 2");
        List<Repository> mockRepositories = Arrays.asList(mockRepo1, mockRepo2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Repository> mockPage = new PageImpl<>(mockRepositories, pageable, 2);

        // Mock the GitProvider method to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        User mockUser = new User();
        mockUser.setId("123");
        mockUser.setUsername("testuser");
        when(mockGitProvider.getCurrentUser()).thenReturn(mockUser);
        when(mockGitProvider.listRepositories(any(), any(), any(), any())).thenReturn(mockPage);

        // When - sort by name ascending
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com&showUserRepositories=true&page=0&size=10&sort=name,asc"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    public void whenCreateRepositoryWithValidParametersThenReturnRepository() throws Exception {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Repository mockCreatedRepo = createMockRepository("test-repo", "Test repository");
        RepositoryRes expectedRepoRes = new RepositoryRes("123456", "test-repo", "Test repository",
                "https://github.com/test/test-repo.git", "git@github.com:test/test-repo.git", "main",
                null, null, null);

        // Configure the mock GitProvider to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.createRepository(any(Repository.class))).thenReturn(mockCreatedRepo);
        when(mockGitProvider.getCurrentUser()).thenReturn(new User("123", "testuser", null, null, null));

        // Create request body
        CreateRepositoryReqRes createRepositoryReq = new CreateRepositoryReqRes();
        createRepositoryReq.setName("test-repo");
        createRepositoryReq.setDescription("Test repository");
        createRepositoryReq.setIsPrivate(false);

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(createRepositoryReq, headers),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        // Parse and verify response
        RepositoryRes actualRepoRes = objectMapper.treeToValue(response.getBody(), RepositoryRes.class);
        assertThat(actualRepoRes).usingRecursiveComparison().isEqualTo(expectedRepoRes);
    }

    @Test
    public void whenCreateRepositoryForOrganizationThenReturnRepository() throws Exception {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        Repository mockCreatedRepo = createMockRepository("org-repo", "Organization repository");
        RepositoryRes expectedRepoRes = new RepositoryRes("123456", "org-repo", "Organization repository",
                "https://github.com/test/org-repo.git", "git@github.com:test/org-repo.git", "main",
                null, null, null);

        // Configure the mock GitProvider to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.createRepository(any(Repository.class))).thenReturn(mockCreatedRepo);

        // Create request body
        CreateRepositoryReqRes createRepositoryReq = new CreateRepositoryReqRes();
        createRepositoryReq.setName("org-repo");
        createRepositoryReq.setDescription("Organization repository");
        createRepositoryReq.setIsPrivate(true);

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com&organizationId=456&organizationName=testorg"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(createRepositoryReq, headers),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        // Parse and verify response
        RepositoryRes actualRepoRes = objectMapper.treeToValue(response.getBody(), RepositoryRes.class);
        assertThat(actualRepoRes).usingRecursiveComparison().isEqualTo(expectedRepoRes);
    }

    @Test
    public void whenGetRepositoryBranchesWithoutProviderBaseUrlThenReturnBadRequest() {
        // Given - providerType and ownerId present but providerBaseUrl missing
        HttpHeaders headers = createTestHeaders();

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories/repo-123/branches?ownerId=owner-1&providerType=GITHUB&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetRepositoryTagsWithValidParametersThenReturnTags() throws Exception {
        HttpHeaders headers = createTestHeaders();

        Repository mockRepo = createMockRepository("my-repo", "My repository");
        Tag mockTag1 = new Tag("v1.0.0", "sha111");
        Tag mockTag2 = new Tag("v2.0.0", "sha222");
        List<Tag> mockTags = Arrays.asList(mockTag1, mockTag2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Tag> mockPage = new PageImpl<>(mockTags, pageable, 2);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.getRepository(eq("repo-123"), eq("owner-1"))).thenReturn(Optional.of(mockRepo));
        when(mockGitProvider.listTags(eq(mockRepo), any(Pageable.class))).thenReturn(mockPage);

        TagRes expectedTag1 = new TagRes("v1.0.0", "sha111");
        TagRes expectedTag2 = new TagRes("v2.0.0", "sha222");

        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories/repo-123/tags?ownerId=owner-1&providerType=GITHUB&providerBaseUrl=https://api.github.com&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("content")).isTrue();
        assertThat(responseBody.has("totalElements")).isTrue();
        assertThat(responseBody.get("totalElements").asInt()).isEqualTo(2);

        JsonNode content = responseBody.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(2);

        TagRes actualTag1 = objectMapper.treeToValue(content.get(0), TagRes.class);
        TagRes actualTag2 = objectMapper.treeToValue(content.get(1), TagRes.class);
        assertThat(actualTag1).usingRecursiveComparison().isEqualTo(expectedTag1);
        assertThat(actualTag2).usingRecursiveComparison().isEqualTo(expectedTag2);
    }

    @Test
    public void whenGetRepositoryTagsWithoutProviderBaseUrlThenReturnBadRequest() {
        HttpHeaders headers = createTestHeaders();

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories/repo-123/tags?ownerId=owner-1&providerType=GITHUB&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetRepositoryTagsWithoutProviderTypeThenReturnBadRequest() {
        HttpHeaders headers = createTestHeaders();

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories/repo-123/tags?ownerId=owner-1&providerBaseUrl=https://api.github.com&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetRepositoryTagsWhenRepositoryNotFoundThenReturnBadRequest() {
        HttpHeaders headers = createTestHeaders();

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.getRepository(eq("missing-repo"), eq("owner-1"))).thenReturn(Optional.empty());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories/missing-repo/tags?ownerId=owner-1&providerType=GITHUB&providerBaseUrl=https://api.github.com&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenCreateRepositoryTagWithValidParametersThenReturnsOk(@TempDir Path tempDir) {
        HttpHeaders headers = createTestHeaders();

        Repository mockRepo = createMockRepository("my-repo", "My repository");
        when(gitProviderFactoryMock.getMockGitProvider().getRepository(eq("repo-123"), eq("owner-1")))
                .thenReturn(Optional.of(mockRepo));
        GitOperation mockGitOperation = stubGitOperationForRepositoryTagFlow(tempDir);

        CreateRepositoryTagReqRes body = new CreateRepositoryTagReqRes();
        body.setName("v1.2.0");
        body.setBranchName("main");
        body.setMessage("Release 1.2.0");
        body.setAuthorName("Tag Author");
        body.setAuthorEmail("tagger@example.com");

        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS,
                        "/repositories/repo-123/tags?ownerId=owner-1&providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(mockGitOperation).addTag(eq(tempDir.toFile()), tagCaptor.capture());
        assertThat(tagCaptor.getValue().getName()).isEqualTo("v1.2.0");
        assertThat(tagCaptor.getValue().getCommitHash()).isEqualTo("HEAD");
        assertThat(tagCaptor.getValue().getMessage()).isEqualTo("Release 1.2.0");
        verify(mockGitOperation).push(tempDir.toFile(), true);
    }

    @Test
    public void whenCreateRepositoryTagWithoutNameThenReturnBadRequest() {
        HttpHeaders headers = createTestHeaders();

        CreateRepositoryTagReqRes body = new CreateRepositoryTagReqRes();
        body.setBranchName("main");
        body.setAuthorName("A");
        body.setAuthorEmail("a@example.com");

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS,
                        "/repositories/repo-123/tags?ownerId=owner-1&providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenCreateRepositoryTagWithCommitHashThenTagPointsToThatCommit(@TempDir Path tempDir) {
        HttpHeaders headers = createTestHeaders();

        Repository mockRepo = createMockRepository("my-repo", "My repository");
        when(gitProviderFactoryMock.getMockGitProvider().getRepository(eq("repo-123"), eq("owner-1")))
                .thenReturn(Optional.of(mockRepo));
        GitOperation mockGitOperation = stubGitOperationForRepositoryTagFlow(tempDir);

        CreateRepositoryTagReqRes body = new CreateRepositoryTagReqRes();
        body.setName("v2.0.0");
        body.setCommitHash("abc123def456");

        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS,
                        "/repositories/repo-123/tags?ownerId=owner-1&providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(mockGitOperation).addTag(eq(tempDir.toFile()), tagCaptor.capture());
        assertThat(tagCaptor.getValue().getName()).isEqualTo("v2.0.0");
        assertThat(tagCaptor.getValue().getCommitHash()).isEqualTo("abc123def456");
        verify(mockGitOperation).push(tempDir.toFile(), true);
    }

    @Test
    public void whenCreateRepositoryTagWithoutProviderBaseUrlThenReturnBadRequest() {
        HttpHeaders headers = createTestHeaders();
        CreateRepositoryTagReqRes body = minimalCreateRepositoryTagRequest();

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories/repo-123/tags?ownerId=owner-1&providerType=GITHUB"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenCreateRepositoryWithoutProviderBaseUrlThenReturnBadRequest() {
        // Given - providerType present but providerBaseUrl missing
        HttpHeaders headers = createTestHeaders();
        CreateRepositoryReqRes createRepositoryReq = new CreateRepositoryReqRes();
        createRepositoryReq.setName("test-repo");
        createRepositoryReq.setDescription("Test repository");
        createRepositoryReq.setIsPrivate(false);

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(createRepositoryReq, headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenCreateRepositoryWithoutRequiredParametersThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Create request body
        CreateRepositoryReqRes createRepositoryReq = new CreateRepositoryReqRes();
        createRepositoryReq.setDescription("Test repository");
        createRepositoryReq.setIsPrivate(false);

        // When - missing required userId and username parameters
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(createRepositoryReq, headers),
                String.class
        );

        // Then - validation should catch missing userId and username at controller level
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenCreateRepositoryWithEmptyRequestBodyThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // When - empty request body
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/repositories?providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new CreateRepositoryReqRes(), headers),
                String.class
        );

        // Then - validation should catch missing required fields in request body
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourceDefinitionsWithValidProviderAndResourceThenReturnDefinitions() throws Exception {
        // Given
        ProviderCustomResourceDefinition mockDefinition1 = new ProviderCustomResourceDefinition("project", "OBJECT", true);
        List<ProviderCustomResourceDefinition> mockDefinitions = Arrays.asList(mockDefinition1);

        // Configure the mock GitProvider to return our test data
        GitProviderExtension mockGitProvider = gitProviderFactoryMock.getMockGitProviderExtension();
        when(mockGitProvider.getProviderCustomResourceDefinitions(any(GitProviderModelResourceType.class)))
                .thenReturn(mockDefinitions);

        // Create expected response object
        ProviderCustomResourceDefinitionRes expectedDefinition = new ProviderCustomResourceDefinitionRes("project", "OBJECT", true);

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources/definitions?resourceName=repository&providerType=BITBUCKET&providerBaseUrl=https://api.bitbucket.org/2.0"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Verify response structure
        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("definitions")).isTrue();

        // Verify definitions array
        JsonNode definitions = responseBody.get("definitions");
        assertThat(definitions.isArray()).isTrue();
        assertThat(definitions.size()).isEqualTo(1);

        // Parse and verify definition
        ProviderCustomResourceDefinitionRes actualDefinition = objectMapper.treeToValue(definitions.get(0), ProviderCustomResourceDefinitionRes.class);
        assertThat(actualDefinition).usingRecursiveComparison().isEqualTo(expectedDefinition);
    }

    @Test
    public void whenGetCustomResourceDefinitionsWithProviderReturningEmptyListThenReturnEmptyList() {
        // Given
        // Configure the mock GitProvider to return empty list (e.g., GitHub doesn't have custom definitions for repository)
        GitProviderExtension mockGitProvider = gitProviderFactoryMock.getMockGitProviderExtension();
        when(mockGitProvider.getProviderCustomResourceDefinitions(any(GitProviderModelResourceType.class)))
                .thenReturn(Arrays.asList());

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources/definitions?resourceName=repository&providerType=GITHUB&providerBaseUrl=https://api.github.com"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Verify response structure
        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("definitions")).isTrue();

        // Verify definitions array is empty
        JsonNode definitions = responseBody.get("definitions");
        assertThat(definitions.isArray()).isTrue();
        assertThat(definitions.size()).isEqualTo(0);
    }

    @Test
    public void whenGetCustomResourceDefinitionsWithInvalidResourceTypeThenReturnBadRequest() {
        // Given - invalid resource type

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources/definitions?resourceName=INVALID&providerType=BITBUCKET&providerBaseUrl=https://api.bitbucket.org/2.0"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourceDefinitionsWithoutResourceNameThenReturnBadRequest() {
        // Given - missing resourceName parameter

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources/definitions?providerType=BITBUCKET"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourceDefinitionsWithoutProviderTypeThenReturnBadRequest() {
        // Given - missing providerType parameter

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources/definitions?resourceName=repository"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourceDefinitionsWithoutProviderBaseUrlThenReturnBadRequest() {
        // Given - providerType present but providerBaseUrl missing

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources/definitions?resourceName=repository&providerType=BITBUCKET"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates test headers with PAT authentication
     */
    private HttpHeaders createTestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-odm-gpauth-type", "PAT");
        headers.set("x-odm-gpauth-param-token", TEST_PAT_TOKEN);
        headers.set("x-odm-gpauth-param-username", TEST_PAT_USERNAME);
        return headers;
    }

    // Helper methods to create mock objects
    private Organization createMockOrganization(String id, String name) {
        Organization org = new Organization();
        org.setId(id);
        org.setName(name);
        org.setUrl("https://github.com/" + name);
        return org;
    }

    private Repository createMockRepository(String name, String description) {
        Repository repo = new Repository();
        repo.setId("123456");
        repo.setName(name);
        repo.setDescription(description);
        repo.setCloneUrlHttp("https://github.com/test/" + name + ".git");
        repo.setCloneUrlSsh("git@github.com:test/" + name + ".git");
        repo.setDefaultBranch("main");
        return repo;
    }

    /**
     * {@link org.opendatamesh.platform.pp.blueprint.blueprint.services.GitProvidersUtilsServiceImpl#createRepositoryTag}
     * runs {@code readRepository} (clone), then {@code addTag} and {@code push} on the clone directory.
     * This mirrors the registry pattern of a dedicated “git op ready for tag” stub instead of inlining Mockito in each test.
     */
    private GitOperation stubGitOperationForRepositoryTagFlow(Path cloneRoot) {
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(gitProviderFactoryMock.getMockGitProvider().gitOperation()).thenReturn(mockGitOperation);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(cloneRoot.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).addTag(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());
        return mockGitOperation;
    }

    private static CreateRepositoryTagReqRes minimalCreateRepositoryTagRequest() {
        CreateRepositoryTagReqRes body = new CreateRepositoryTagReqRes();
        body.setName("v1.0.0");
        body.setAuthorName("A");
        body.setAuthorEmail("a@example.com");
        return body;
    }

    @Test
    public void whenGetCustomResourcesWithValidProviderAndResourceTypeThenReturnResources() throws Exception {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Setup mock data
        ProviderCustomResource mockResource1 = createMockCustomResource("project-1", "Project 1");
        ProviderCustomResource mockResource2 = createMockCustomResource("project-2", "Project 2");
        List<ProviderCustomResource> mockResources = Arrays.asList(mockResource1, mockResource2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProviderCustomResource> mockPage = new PageImpl<>(mockResources, pageable, 2);

        // Configure the mock GitProvider to return our test data
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.getProviderCustomResources(any(String.class), any(), any(Pageable.class)))
                .thenReturn(mockPage);

        // Create expected response objects
        ProviderCustomResourceRes expectedResource1 = new ProviderCustomResourceRes("project-1", "Project 1", null);
        ProviderCustomResourceRes expectedResource2 = new ProviderCustomResourceRes("project-2", "Project 2", null);

        // When
        ResponseEntity<JsonNode> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources?resourceType=project&providerType=BITBUCKET&providerBaseUrl=https://api.bitbucket.org/2.0&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Verify response structure
        JsonNode responseBody = response.getBody();
        assertThat(responseBody.has("content")).isTrue();
        assertThat(responseBody.has("totalElements")).isTrue();
        assertThat(responseBody.get("totalElements").asInt()).isEqualTo(2);

        // Verify content array
        JsonNode content = responseBody.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(2);

        // Parse and verify first resource
        ProviderCustomResourceRes actualResource1 = objectMapper.treeToValue(content.get(0), ProviderCustomResourceRes.class);
        assertThat(actualResource1.getIdentifier()).isEqualTo(expectedResource1.getIdentifier());
        assertThat(actualResource1.getDisplayName()).isEqualTo(expectedResource1.getDisplayName());

        // Parse and verify second resource
        ProviderCustomResourceRes actualResource2 = objectMapper.treeToValue(content.get(1), ProviderCustomResourceRes.class);
        assertThat(actualResource2.getIdentifier()).isEqualTo(expectedResource2.getIdentifier());
        assertThat(actualResource2.getDisplayName()).isEqualTo(expectedResource2.getDisplayName());
    }

    @Test
    public void whenGetCustomResourcesWithoutResourceTypeThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // When - missing required resourceType parameter
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources?providerType=BITBUCKET&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourcesWithoutProviderTypeThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // When - missing required providerType parameter
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources?resourceType=project&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourcesWithoutProviderBaseUrlThenReturnBadRequest() {
        // Given - providerType present but providerBaseUrl missing
        HttpHeaders headers = createTestHeaders();

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources?resourceType=project&providerType=BITBUCKET&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenGetCustomResourcesWithUnsupportedResourceTypeThenReturnBadRequest() {
        // Given
        HttpHeaders headers = createTestHeaders();

        // Configure the mock GitProvider to throw BadRequestException for unsupported resource type
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.getProviderCustomResources(any(String.class), any(), any(Pageable.class)))
                .thenThrow(new BadRequestException("Unsupported retrieval for resource type: unsupported"));

        // When - unsupported resource type
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.GIT_PROVIDERS, "/custom-resources?resourceType=unsupported&providerType=BITBUCKET&providerBaseUrl=https://api.bitbucket.org/2.0&page=0&size=10"),
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Helper method to create mock custom resource
    private ProviderCustomResource createMockCustomResource(String identifier, String displayName) {
        ProviderCustomResource resource = new ProviderCustomResource();
        resource.setIdentifier(identifier);
        resource.setDisplayName(displayName);
        resource.setContent(null); // Can be set to a JsonNode if needed
        return resource;
    }

}
