package com.gitcolab.services;

import com.gitcolab.dto.UserDTO;
import com.gitcolab.dto.inhouse.request.ContributorRequest;
import com.gitcolab.dto.inhouse.request.SendProjectRequest;
import com.gitcolab.dto.inhouse.response.MessageResponse;
import com.gitcolab.dto.toolExchanges.IssueData;
import com.gitcolab.dto.toolExchanges.RepositoryData;
import com.gitcolab.dto.toolExchanges.request.GithubIssueEventRequest;
import com.gitcolab.dto.toolExchanges.request.ProjectCreationRequest;
import com.gitcolab.entity.EnumIntegrationType;
import com.gitcolab.entity.Project;
import com.gitcolab.entity.ToolTokenManager;
import com.gitcolab.repositories.ProjectRepository;
import com.gitcolab.repositories.ToolTokenManagerRepository;
import com.gitcolab.utilities.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
public class ProjectServiceTest {

    @Mock
    private ToolTokenManagerRepository toolTokenManagerRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GithubService githubService;

    @Mock
    private UserService userService;

    @Mock
    private EmailSender emailSender;

    @Mock
    private AtlassianService atlassianService;

    @InjectMocks
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateProject_RepositoryExists() {
        String repositoryName = "test-repo";
        String githubToken = "test-token";
        ProjectCreationRequest request = new ProjectCreationRequest();
        request.setRepositoryName(repositoryName);
        request.setGithubToken(githubToken);
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());

        GHRepository ghRepository = mock(GHRepository.class);
        when(githubService.getRepositoryByName(repositoryName, githubToken)).thenReturn(ghRepository);

        ResponseEntity<?> response = projectService.createProject(request, userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Repository already exists.", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetAllProjects() {
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());
        List<Map<String, Object>> projects = new ArrayList<>();
        when(projectRepository.getAllProject(userDetails.getId())).thenReturn(projects);
    
        ResponseEntity<?> response = projectService.getAllProjects(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(projects, response.getBody());
    }

    @Test
    void testGetProjectById() {
        int projectId = 1;
        Project project = new Project();
        project.setRepositoryName("test-repo");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        Optional<Project> response = projectService.getProjectById(projectId);
        assertTrue(response.isPresent());
        assertEquals("test-repo", response.get().getRepositoryName());
    }

    @Test
    void testGetDashboardData() {
        long userId = 1L;
        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("totalRepositories", 0);
        dashboardData.put("totalProjectContributions", 0);
        dashboardData.put("numberOfFollowers", 0);
        dashboardData.put("topCommittedRepositories", "");
        dashboardData.put("totalProjectOwnership", 0);
        when(projectRepository.getAllProject(userId)).thenReturn(new ArrayList<>());
        when(projectRepository.getGithubTokenByUserId(userId)).thenReturn("test-token");
    
        ResponseEntity<?> response = projectService.getDashboardData(userId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    

    @Test
    void testGetDashboardAllData() throws IOException {
        long userId = 1L;
        String githubAuthToken = "test-token";
        GitHub gitHub = mock(GitHub.class);
        GHMyself ghMyself = mock(GHMyself.class);
        when(projectRepository.getGithubTokenByUserId(userId)).thenReturn(githubAuthToken);
        when(githubService.getGithubUserByToken(githubAuthToken)).thenReturn(gitHub);
        when(gitHub.getMyself()).thenReturn(ghMyself);
        when(ghMyself.getFollowersCount()).thenReturn(10);
        when(ghMyself.getPublicRepoCount()).thenReturn(5);
        when(githubService.topCommittedRepositories(ghMyself, 10)).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = projectService.getDashboardData(userId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, ((Map<?, ?>) response.getBody()).get("numberOfFollowers"));
        assertEquals(5, ((Map<?, ?>) response.getBody()).get("totalRepositories"));
        assertEquals(Collections.emptyList(), ((Map<?, ?>) response.getBody()).get("topCommittedRepositories"));
    }



    @Test
    void testCreateProject_RepositoryDoesNotExist() throws IOException {
        String repositoryName = "test-repo";
        String githubToken = "test-token";
        ProjectCreationRequest request = new ProjectCreationRequest();
        request.setRepositoryName(repositoryName);
        request.setGithubToken(githubToken);
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());

        GHRepository ghRepository = null;
        when(githubService.getRepositoryByName(repositoryName, githubToken)).thenReturn(ghRepository);

        ResponseEntity<?> response = projectService.createProject(request, userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Something went wrong", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetContributors() {
        int projectId = 1;
        List<Map<String, Object>> contributors = new ArrayList<>();
        when(projectRepository.getAllContributors(projectId)).thenReturn(contributors);
    
        ResponseEntity<?> response = projectService.getContributors(projectId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(contributors, response.getBody());
    }

    @Test
    void testAddContributor_InvalidRequest() {
        ContributorRequest request = new ContributorRequest();
        request.setUsername("");
        request.setEmail("");
        ResponseEntity<?> response = projectService.addContributor(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testAddContributor_RepositoryNotExists() {
        ContributorRequest request = new ContributorRequest();
        request.setUsername("test-user");
        request.setEmail("test-email");
        request.setGithubAuthToken("test-token");
        request.setRepositoryName("test-repo");
        GHRepository ghRepository = null;
        when(githubService.getRepositoryByName(request.getRepositoryName(), request.getGithubAuthToken())).thenReturn(ghRepository);
        ResponseEntity<?> response = projectService.addContributor(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Repository not exists", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testAddContributor_Success() {
        ContributorRequest request = new ContributorRequest();
        request.setUsername("test-user");
        request.setEmail("test-email");
        request.setGithubAuthToken("test-token");
        request.setRepositoryName("test-repo");
        GHRepository ghRepository = mock(GHRepository.class);
        when(githubService.getRepositoryByName(request.getRepositoryName(), request.getGithubAuthToken())).thenReturn(ghRepository);
        when(githubService.addCollaborator(any(), any(), any())).thenReturn(true);
        when(projectRepository.findByRepositoryName(any())).thenReturn(Optional.of(new Project()));
        when(userService.getUser(any())).thenReturn(new UserDTO());
        ResponseEntity<?> response = projectService.addContributor(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Contributor added successfully", ((MessageResponse) response.getBody()).getMessage());
        verify(emailSender, times(1)).sendEmail(any(), any(), any());
    }

    @Test
    void testCreateJira() {
        GithubIssueEventRequest githubIssueEventRequest = new GithubIssueEventRequest();
        RepositoryData repositoryData = new RepositoryData();
        repositoryData.setFull_name("test/test");
        githubIssueEventRequest.setRepository(repositoryData);
        ResponseEntity<?> response = projectService.createJira(githubIssueEventRequest);
        assertEquals(null, response);
    }
    
    @Test
    void testAddContributor() {
        ContributorRequest contributorRequest = new ContributorRequest();
        contributorRequest.setUsername("test-user");
        contributorRequest.setEmail("test-email");
        contributorRequest.setGithubAuthToken("test-token");
        contributorRequest.setRepositoryName("test-repo");
        GHRepository ghRepository = mock(GHRepository.class);
        when(githubService.getRepositoryByName(contributorRequest.getRepositoryName(), contributorRequest.getGithubAuthToken())).thenReturn(ghRepository);
        ResponseEntity<?> response = projectService.addContributor(contributorRequest);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testGetProjectContributorMap() {
        int userId = 1;
        int level = 1;
        ResponseEntity<?> response = projectService.getProjectContributorMap(userId, level);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testCreateProject_JiraBoardExists() {
        ProjectCreationRequest request = new ProjectCreationRequest();
        request.setJiraBoardName("test-board");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());
        when(projectRepository.isJiraBoardExist(request.getJiraBoardName(), String.valueOf(userDetails.getId()))).thenReturn(true);
        ResponseEntity<?> response = projectService.createProject(request, userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Jira Board With Given Name Already Exists", ((MessageResponse) response.getBody()).getMessage());
    }
    
    @Test
    void testCreateProject_AtlassianRequiredButTokenInvalid() {
        ProjectCreationRequest request = new ProjectCreationRequest();
        request.setAtlassianRequired(true);
        request.setAtlassianToken(null);
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());
        ResponseEntity<?> response = projectService.createProject(request, userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Atlassian auth code is invalid.", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testCreateProject_GithubUserNotExist() {
        ProjectCreationRequest request = new ProjectCreationRequest();
        request.setGithubToken("invalid-token");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());
        when(githubService.getGithubUserByToken(request.getGithubToken())).thenReturn(null);
        ResponseEntity<?> response = projectService.createProject(request, userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testAddContributor_InvalidUsernameOrEmail() {
        ContributorRequest request = new ContributorRequest();
        request.setUsername("");
        request.setEmail("");
        ResponseEntity<?> response = projectService.addContributor(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testAddContributor_RepositoryNotExist() {
        ContributorRequest request = new ContributorRequest();
        request.setRepositoryName("non-exist-repo");
        request.setGithubAuthToken("test-token");
        GHRepository ghRepository = null;
        when(githubService.getRepositoryByName(request.getRepositoryName(), request.getGithubAuthToken())).thenReturn(ghRepository);
        ResponseEntity<?> response = projectService.addContributor(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetProjectContributorMap_InvalidLevel() {
        int userId = 1;
        int level = 0;
        ResponseEntity<?> response = projectService.getProjectContributorMap(userId, level);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Invalid level", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetDashboardData_GithubUserNull() {
        long userId = 1L;
        String githubAuthToken = "test-token";
        when(projectRepository.getGithubTokenByUserId(userId)).thenReturn(githubAuthToken);
        when(githubService.getGithubUserByToken(githubAuthToken)).thenReturn(null);
        ResponseEntity<?> response = projectService.getDashboardData(userId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Map<?, ?>) response.getBody()).get("numberOfFollowers"));
        assertEquals(0, ((Map<?, ?>) response.getBody()).get("totalRepositories"));
        assertEquals("", ((Map<?, ?>) response.getBody()).get("topCommittedRepositories"));
    }

    @Test
    void testGetProjectById_ProjectNotFound() {
        int projectId = 1;
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        Optional<Project> response = projectService.getProjectById(projectId);
        assertTrue(response.isEmpty());
    }

    @Test
    void testGetGithubTokenByUserId_TokenNull() {
        Long userId = 1L;
        when(projectRepository.getGithubTokenByUserId(userId)).thenReturn(null);
        String response = projectRepository.getGithubTokenByUserId(userId);
        assertNull(response);
    }
    
    @Test
    void testGetGithubTokenByUserId_TokenNotNull() {
        Long userId = 1L;
        String expectedToken = "test-token";
        when(projectRepository.getGithubTokenByUserId(userId)).thenReturn(expectedToken);
        String response = projectRepository.getGithubTokenByUserId(userId);
        assertEquals(expectedToken, response);
    }

    @Test
    void testSendProjectRequest_UserNotExist() {
        SendProjectRequest sendProjectRequest = new SendProjectRequest();
        sendProjectRequest.setProjectOwner("test-owner");
        String username = "test-user";
        when(userService.getUser(sendProjectRequest.getProjectOwner())).thenReturn(null);
        ResponseEntity<?> response = projectService.sendProjectRequest(sendProjectRequest, username);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Something went wrong!", ((MessageResponse) response.getBody()).getMessage());
    }
    @Test
    void testSendProjectRequest_Success() {
        SendProjectRequest sendProjectRequest = new SendProjectRequest();
        sendProjectRequest.setProjectOwner("test-owner");
        String username = "test-user";
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail("test-email");
        when(userService.getUser(sendProjectRequest.getProjectOwner())).thenReturn(userDTO);
        ResponseEntity<?> response = projectService.sendProjectRequest(sendProjectRequest, username);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Invitation sent successfully", ((MessageResponse) response.getBody()).getMessage());
        verify(emailSender, times(1)).sendEmail(any(), any(), any());
    }
    @Test
    void testCreateJira_ToolTokenManagerEmpty() {
        GithubIssueEventRequest githubIssueEventRequest = new GithubIssueEventRequest();
        RepositoryData repositoryData = new RepositoryData();
        repositoryData.setFull_name("test/test");
        githubIssueEventRequest.setRepository(repositoryData);
        when(toolTokenManagerRepository.getByRepositoryOwner(any(), any())).thenReturn(Optional.empty());
        ResponseEntity<?> response = projectService.createJira(githubIssueEventRequest);
        assertNull(response);
    }

    @Test
    void testSendProjectRequest_ValidRequest() {
        String projectOwner = "project-owner";
        String repositoryName = "test-repo";
        String username = "test-user";
        String email = "project.owner@example.com";

        SendProjectRequest sendProjectRequest = new SendProjectRequest();
        sendProjectRequest.setProjectOwner(projectOwner);
        sendProjectRequest.setRepositoryName(repositoryName);

        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(projectOwner);
        userDTO.setEmail(email);

        when(userService.getUser(projectOwner)).thenReturn(userDTO);

        ResponseEntity<?> response = projectService.sendProjectRequest(sendProjectRequest, username);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Invitation sent successfully", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testSendProjectRequest_InvalidRequest() {
        String projectOwner = null;
        String repositoryName = "test-repo";
        String username = "test-user";

        SendProjectRequest sendProjectRequest = new SendProjectRequest();
        sendProjectRequest.setProjectOwner(projectOwner);
        sendProjectRequest.setRepositoryName(repositoryName);

        ResponseEntity<?> response = projectService.sendProjectRequest(sendProjectRequest, username);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testSendProjectRequest_ProjectOwnerNotFound() {
        String projectOwner = "project-owner-not-found";
        String repositoryName = "test-repo";
        String username = "test-user";

        SendProjectRequest sendProjectRequest = new SendProjectRequest();
        sendProjectRequest.setProjectOwner(projectOwner);
        sendProjectRequest.setRepositoryName(repositoryName);

        when(userService.getUser(projectOwner)).thenReturn(null);

        ResponseEntity<?> response = projectService.sendProjectRequest(sendProjectRequest, username);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Something went wrong!", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void testGetProjectContributorMap_DistancesDoesNotContainNeighbor() {
        int userId = 1;
        int level = 2;

        List<Map<String, Object>> projectContributorMap = new ArrayList<>();


        when(projectRepository.getProjectContributorMap()).thenReturn(projectContributorMap);

        ResponseEntity<?> response = projectService.getProjectContributorMap(userId, level);
        assertEquals(HttpStatus.OK, response.getStatusCode());


        Set<Map<String, Object>> projectList = (Set<Map<String, Object>>) response.getBody();
        assertNotNull(projectList);
        assertTrue(projectList.isEmpty());
    }

    @Test
    void testGetProjectContributorMap_DistancesContainsNeighbor() {
        int userId = 1;
        int level = 1;

        List<Map<String, Object>> projectContributorMap = new ArrayList<>();



        List<Map<String, Object>> projectsOfNeighbor = new ArrayList<>();


        when(projectRepository.getProjectContributorMap()).thenReturn(projectContributorMap);
        when(projectRepository.getAllProject(anyLong())).thenReturn(projectsOfNeighbor);

        ResponseEntity<?> response = projectService.getProjectContributorMap(userId, level);
        assertEquals(HttpStatus.OK, response.getStatusCode());


        Set<Map<String, Object>> projectList = (Set<Map<String, Object>>) response.getBody();
        assertNotNull(projectList);

    }



    @Test
    void createProject_withNullProjectCreationRequest_throwsException() {
        ProjectService projectService = new ProjectService(
                toolTokenManagerRepository,
                githubService,
                projectRepository,
                atlassianService,
                userService
        );
        assertThrows(NullPointerException.class, () -> projectService.createProject(null, null));
    }

    @Test
    void addContributor_withNonExistentUsername_returnsFalse() {
        GithubService githubService = mock(GithubService.class);
        when(githubService.addCollaborator(anyString(), anyString(), anyString())).thenReturn(false);

        ProjectService projectService = new ProjectService(
                toolTokenManagerRepository,
                githubService,
                projectRepository,
                atlassianService,
                userService
        );
        assertEquals(400, projectService.addContributor(new ContributorRequest()).getStatusCodeValue());
    }

    @Test
    void testCreateProject_RepositoryCreationFailed() {
        ProjectCreationRequest request = new ProjectCreationRequest();
        request.setRepositoryName("test-repo");
        request.setGithubToken("test-token");
        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "test-user", "test-email", "test-password", Collections.emptyList());
        when(githubService.generateRepository(request.getRepositoryName(), request.getGithubToken())).thenReturn(false);
        ResponseEntity<?> response = projectService.createProject(request, userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Something went wrong", ((MessageResponse) response.getBody()).getMessage());
    }
}
