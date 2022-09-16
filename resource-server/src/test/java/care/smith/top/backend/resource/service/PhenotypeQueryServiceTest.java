package care.smith.top.backend.resource.service;

import care.smith.top.backend.model.*;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class PhenotypeQueryServiceTest extends Neo4jTest {
  static List<DataSource> dataSources;
  @Autowired PhenotypeQueryService queryService;
  @Autowired StorageProvider storageProvider;
  @Autowired OrganisationService organisationService;
  @Autowired RepositoryService repositoryService;

  @BeforeAll
  static void setup() {
    dataSources =
        Arrays.asList(
            new DataSource().id("Test_Data_Source_1").title("Test Data Source 1"),
            new DataSource().id("Test_Data_Source_2").title("Test Data Source 2"));
  }

  @Test
  void executeQuery() {
    Organisation orga = organisationService.createOrganisation(new Organisation().id("orga_1"));
    Repository repo =
        repositoryService.createRepository(orga.getId(), new Repository().id("repo_1"), null);
    Query query =
        new Query()
            .id(UUID.randomUUID())
            ._configuration(
                new QueryConfiguration()
                    .addSourcesItem(new DataSource().id(dataSources.get(0).getId())));

    assertThatThrownBy(() -> queryService.enqueueQuery(orga.getId(), "invalid", query))
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

    UUID queryId = queryService.enqueueQuery(orga.getId(), repo.getId(), query);
    assertThat(queryId).isEqualTo(query.getId());
    await()
        .atMost(100, TimeUnit.SECONDS)
        .until(() -> storageProvider.getJobStats().getSucceeded() == 1);

    assertThat(queryService.getQueryResult(orga.getId(), repo.getId(), queryId))
        .satisfies(
            r -> {
              assertThat(r.getId()).isEqualTo(queryId);
              assertThat(r.getCreatedAt()).isNotNull();
              assertThat(r.getFinishedAt()).isNotNull();
              assertThat(r.getCreatedAt().compareTo(r.getFinishedAt())).isLessThanOrEqualTo(0);
              assertThat(r.getCount()).isNotNull();
              assertThat(r.getState()).isEqualTo(QueryState.FINISHED);
            });

    queryService.deleteQuery(orga.getId(), repo.getId(), queryId);
    assertThat(storageProvider.getJobStats().getSucceeded()).isEqualTo(0);
  }

  @Test
  void getDataAdapterConfig() {
    String id = dataSources.get(0).getId();
    assertThat(queryService.getDataAdapterConfig("invalid")).isNotPresent();
    assertThat(queryService.getDataAdapterConfig(id))
        .satisfies(
            a -> {
              assertThat(a).isPresent();
              assertThat(a.get().getId()).isEqualTo(id);
            });
  }

  @Test
  void getDataAdapterConfigs() {
    assertThat(queryService.getDataAdapterConfigs())
        .satisfiesExactly(
            a -> assertThat(a.getId()).isEqualTo(dataSources.get(0).getId()),
            a -> assertThat(a.getId()).isEqualTo(dataSources.get(1).getId()));
  }

  @Test
  void getDataSources() {
    assertThat(queryService.getDataSources()).isEqualTo(dataSources);
  }
}
