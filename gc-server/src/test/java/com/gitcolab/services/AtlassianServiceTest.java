package com.gitcolab.services;

import com.gitcolab.configurations.ClientConfig;
import com.gitcolab.dto.UserDTO;
import com.gitcolab.dto.inhouse.request.SendProjectRequest;
import com.gitcolab.dto.inhouse.response.MessageResponse;
import com.gitcolab.dto.toolExchanges.AtlassianUser;
import com.gitcolab.dto.toolExchanges.IssueData;
import com.gitcolab.dto.toolExchanges.RepositoryData;
import com.gitcolab.dto.toolExchanges.request.GetAccessTokenRequest;
import com.gitcolab.dto.toolExchanges.request.GithubIssueEventRequest;
import com.gitcolab.dto.toolExchanges.request.ProjectCreationRequest;
import com.gitcolab.dto.toolExchanges.response.AccessibleResource;
import com.gitcolab.dto.toolExchanges.response.GetAccessTokenResponse;
import com.gitcolab.dto.toolExchanges.response.MySelfResponse;
import com.gitcolab.dto.toolExchanges.response.ProjectResponse;
import com.gitcolab.entity.ToolTokenManager;
import com.gitcolab.entity.User;
import com.gitcolab.repositories.ProjectRepository;
import com.gitcolab.repositories.ToolTokenManagerRepository;
import com.gitcolab.repositories.UserRepository;

import reactor.core.publisher.Mono;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@SpringBootTest
public class AtlassianServiceTest {

    @Mock
    private ToolTokenManagerRepository toolTokenManagerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClientConfig clientConfig;

    @Mock
    private AtlassianServiceClient atlassianServiceClient;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private AtlassianService atlassianService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAccessToken() {
        GetAccessTokenRequest getAccessTokenRequest = new GetAccessTokenRequest();
        getAccessTokenRequest.setCode("code");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "user", "user@example.com", "password", null);
        GetAccessTokenResponse getAccessTokenResponse = new GetAccessTokenResponse();
        getAccessTokenResponse.setAccess_token("access_token");
        when(atlassianServiceClient.getAccessToken(getAccessTokenRequest)).thenReturn(ResponseEntity.ok(getAccessTokenResponse));
        ResponseEntity<?> response = atlassianService.getAccessToken(getAccessTokenRequest, userDetails);
        assertEquals(400, response.getStatusCodeValue());
        verify(toolTokenManagerRepository, times(1)).save(any(ToolTokenManager.class));
    }

    @Test
    void testGetAccessibleResources() {
        AccessibleResource accessibleResource = new AccessibleResource();
        accessibleResource.setId("id");
        when(atlassianServiceClient.getAccessibleResources("Bearer token")).thenReturn(Arrays.asList(accessibleResource));
        Optional<AccessibleResource> result = atlassianService.getAccessibleResources("Bearer token");
        assertTrue(result.isPresent());
        assertEquals("id", result.get().getId());
    }


    @Test
    void testGetUserDetails() {
        MySelfResponse mySelfResponse = new MySelfResponse();
        mySelfResponse.setAccountId("accountId");
        when(atlassianServiceClient.getUserDetails("cloudId", "Bearer token")).thenReturn(mySelfResponse);
        Optional<MySelfResponse> result = atlassianService.getUserDetails("Bearer token", "cloudId");
        assertTrue(result.isPresent());
        assertEquals("accountId", result.get().getAccountId());
    }

    @Test
    void testCreateAtlassianProject() {
        ProjectCreationRequest projectCreationRequest = new ProjectCreationRequest();
        projectCreationRequest.setAtlassianToken("token");
        projectCreationRequest.setRepositoryName("TestRepo");

        AccessibleResource accessibleResource = new AccessibleResource();
        accessibleResource.setId("id");

        MySelfResponse mySelfResponse = new MySelfResponse();
        mySelfResponse.setAccountId("accountId");

        when(atlassianServiceClient.getAccessibleResources("Bearer token")).thenReturn(Arrays.asList(accessibleResource));
        when(atlassianServiceClient.getUserDetails("id", "Bearer token")).thenReturn(mySelfResponse);

        Mono<ProjectResponse> result = atlassianService.createAtlassianProject(projectCreationRequest);

        assertNull(result);
    }

    @Test
    void testCreateIssue() {
        GithubIssueEventRequest githubIssueEventRequest = new GithubIssueEventRequest();
        RepositoryData repositoryData = new RepositoryData();
        repositoryData.setName("TestRepo");
        githubIssueEventRequest.setRepository(repositoryData);
        IssueData issueData = new IssueData();
        issueData.setTitle("Test Issue");
        AtlassianUser atlassianUser = new AtlassianUser();
        atlassianUser.setLogin("TestUser");
        issueData.setUser(atlassianUser);
        githubIssueEventRequest.setIssue(issueData);
        githubIssueEventRequest.setAction("open");
        AccessibleResource accessibleResource = new AccessibleResource();
        accessibleResource.setId("id");
        when(atlassianServiceClient.getAccessibleResources("Bearer token")).thenReturn(Arrays.asList(accessibleResource));
        MySelfResponse mySelfResponse = new MySelfResponse();
        mySelfResponse.setAccountId("accountId");
        when(atlassianServiceClient.getUserDetails("id", "Bearer token")).thenReturn(mySelfResponse);
        ResponseEntity<?> response = atlassianService.createIssue(githubIssueEventRequest, "projectId", "Bearer token");
        assertNull(response);
    }


    @Test
    void testGenerateKey() {
        String key = AtlassianService.generateKey("TestRepo");
        assertNotNull(key);
        assertTrue(key.startsWith("TESTR"));
    }

    @Test
    void testGetAccessToken_InvalidCode() {
        GetAccessTokenRequest getAccessTokenRequest = new GetAccessTokenRequest();
        getAccessTokenRequest.setCode("");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "user", "user@example.com", "password", null);
        ResponseEntity<?> response = atlassianService.getAccessToken(getAccessTokenRequest, userDetails);
        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof MessageResponse);
        assertEquals("Atlassian auth code is invalid.", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetAccessToken_NoIntegrationPresent() {
        GetAccessTokenRequest getAccessTokenRequest = new GetAccessTokenRequest();
        getAccessTokenRequest.setCode("code");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "user", "user@example.com", "password", null);
        GetAccessTokenResponse getAccessTokenResponse = new GetAccessTokenResponse();
        getAccessTokenResponse.setAccess_token("access_token");
        when(atlassianServiceClient.getAccessToken(getAccessTokenRequest)).thenReturn(ResponseEntity.ok(getAccessTokenResponse));
        when(toolTokenManagerRepository.getByEmail(anyString(), any())).thenReturn(Optional.empty());
        ResponseEntity<?> response = atlassianService.getAccessToken(getAccessTokenRequest, userDetails);
        assertEquals(400, response.getStatusCodeValue());
        verify(toolTokenManagerRepository, times(1)).save(any(ToolTokenManager.class));
    }

    @Test
    void testGetAccessToken_IntegrationPresent() {
        GetAccessTokenRequest getAccessTokenRequest = new GetAccessTokenRequest();
        getAccessTokenRequest.setCode("code");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "user", "user@example.com", "password", null);
        GetAccessTokenResponse getAccessTokenResponse = new GetAccessTokenResponse();
        getAccessTokenResponse.setAccess_token("access_token");
        when(atlassianServiceClient.getAccessToken(getAccessTokenRequest)).thenReturn(ResponseEntity.ok(getAccessTokenResponse));
        when(toolTokenManagerRepository.getByEmail(anyString(), any())).thenReturn(Optional.of(new ToolTokenManager()));

        ResponseEntity<?> response = atlassianService.getAccessToken(getAccessTokenRequest, userDetails);

        assertEquals(400, response.getStatusCodeValue());
        assertFalse(response.getBody() instanceof ResponseEntity);

        
        verify(toolTokenManagerRepository, times(1)).update(any(ToolTokenManager.class));
    }

    @Test
    void testGetAccessToken_NoAccessibleResource() {
        GetAccessTokenRequest getAccessTokenRequest = new GetAccessTokenRequest();
        getAccessTokenRequest.setCode("code");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "user", "user@example.com", "password", null);
        GetAccessTokenResponse getAccessTokenResponse = new GetAccessTokenResponse();
        getAccessTokenResponse.setAccess_token("access_token");
        when(atlassianServiceClient.getAccessToken(getAccessTokenRequest)).thenReturn(ResponseEntity.ok(getAccessTokenResponse));
        when(toolTokenManagerRepository.getByEmail(anyString(), any())).thenReturn(Optional.of(new ToolTokenManager()));
        when(atlassianServiceClient.getAccessibleResources(anyString())).thenReturn(Arrays.asList());

        ResponseEntity<?> response = atlassianService.getAccessToken(getAccessTokenRequest, userDetails);

        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof MessageResponse);
        assertEquals("No Atlassian Site exist for User, Please create new Atlassian Site!", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetAccessToken_AllConditionsPass() {
        GetAccessTokenRequest getAccessTokenRequest = new GetAccessTokenRequest();
        getAccessTokenRequest.setCode("code");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "user", "user@example.com", "password", null);
        GetAccessTokenResponse getAccessTokenResponse = new GetAccessTokenResponse();
        getAccessTokenResponse.setAccess_token("access_token");
        when(atlassianServiceClient.getAccessToken(getAccessTokenRequest)).thenReturn(ResponseEntity.ok(getAccessTokenResponse));
        when(toolTokenManagerRepository.getByEmail(anyString(), any())).thenReturn(Optional.empty());
        when(atlassianServiceClient.getAccessibleResources(anyString())).thenReturn(Arrays.asList(new AccessibleResource()));

        ResponseEntity<?> response = atlassianService.getAccessToken(getAccessTokenRequest, userDetails);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof ResponseEntity);

        
        verify(toolTokenManagerRepository, times(1)).save(any(ToolTokenManager.class));
    }

    @Test
    void testGetAccessibleResources_NonEmptyList() {
        String bearerToken = "example_bearer_token";

        
        List<AccessibleResource> accessibleResources = new ArrayList<>();
        accessibleResources.add(new AccessibleResource());
        accessibleResources.add(new AccessibleResource("id1","url","name","avatarURL"));

        
        when(atlassianServiceClient.getAccessibleResources(bearerToken)).thenReturn(accessibleResources);

        
        Optional<AccessibleResource> result = atlassianService.getAccessibleResources(bearerToken);

        
        assertTrue(result.isPresent());
        assertEquals("id1", result.get().getId());

        
        verify(atlassianServiceClient, times(1)).getAccessibleResources(bearerToken);
    }

    @Test
    void testGetAccessibleResources_NoResources() {
        when(atlassianServiceClient.getAccessibleResources("Bearer token")).thenReturn(Collections.emptyList());
        Optional<AccessibleResource> result = atlassianService.getAccessibleResources("Bearer token");
        assertFalse(result.isPresent());
    }

    @Test
    void testGetUserDetails_NoUserDetails() {
        when(atlassianServiceClient.getUserDetails("cloudId", "Bearer token")).thenReturn(null);
        assertThrows(NullPointerException.class, () -> atlassianService.getUserDetails("Bearer token", "cloudId"));
    }


    @Test
    void testCreateAtlassianProject_NoAccessibleResource() {
        ProjectCreationRequest projectCreationRequest = new ProjectCreationRequest();
        projectCreationRequest.setRepositoryName("TestRepo");
        projectCreationRequest.setAtlassianToken("token");
        when(atlassianServiceClient.getAccessibleResources("Bearer token")).thenReturn(Collections.emptyList());
        assertThrows(Exception.class, () -> atlassianService.createAtlassianProject(projectCreationRequest).block());
    }    

    @Test
    void testCreateIssue_NoAccessibleResource() {
        GithubIssueEventRequest githubIssueEventRequest = new GithubIssueEventRequest();
        RepositoryData repositoryData = new RepositoryData();
        repositoryData.setName("TestRepo");
        githubIssueEventRequest.setRepository(repositoryData);
        IssueData issueData = new IssueData();
        issueData.setTitle("Test Issue");
        githubIssueEventRequest.setIssue(issueData);
        githubIssueEventRequest.setAction("open");
        when(atlassianServiceClient.getAccessibleResources("Bearer token")).thenReturn(Collections.emptyList());
        ResponseEntity<?> response = atlassianService.createIssue(githubIssueEventRequest, "projectId", "Bearer token");
        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof MessageResponse);
        assertEquals("Something went wrong while creating JIRA!", ((MessageResponse) response.getBody()).getMessage());
    }


    @Test
    void testGenerateKey_EmptyName() {
        assertThrows(IllegalArgumentException.class, () -> AtlassianService.generateKey(""));
    }

    @Test
    void testGenerateKey_NullName() {
        assertThrows(IllegalArgumentException.class, () -> AtlassianService.generateKey(null));
    }

}
