package care.smith.top.backend.service;

import care.smith.top.backend.model.Expression;
import care.smith.top.backend.model.*;
import care.smith.top.backend.repository.EntityRepository;
import care.smith.top.backend.repository.RepositoryRepository;
import care.smith.top.backend.util.ApiModelMapper;
import care.smith.top.phenotype2r.Phenotype2RConverter;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class EntityService implements ContentService {
  @Value("${spring.paging.page-size:10}")
  private int pageSize;

  @Autowired private EntityRepository entityRepository;
  @Autowired private RepositoryRepository repositoryRepository;
  @Autowired private RepositoryService repositoryService;

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  //  private static Statement findEntitiesMatchingConditionStatement(
  //      String repositoryId,
  //      String name,
  //      List<EntityType> type,
  //      DataType dataType,
  //      boolean primaryOnly,
  //      Integer page) {
  //    /*
  //     * It would be better to use autogenerated node classes here, but there is currently a bug
  // that
  //     * prevents instantiation of our model. See:
  //     * https://github.com/neo4j-contrib/cypher-dsl/issues/335
  //     */
  //    Node c = Cypher.node("Class").named("c");
  //    Node cv = Cypher.node("ClassVersion").named("cv");
  //    Node a = Cypher.node("Annotation").named("a");
  //    Node r = Cypher.node("Repository").named("r");
  //    Node aTitle = a.withProperties("property", Cypher.anonParameter("title")).named("title");
  //    Node aSynonym = a.withProperties("property",
  // Cypher.anonParameter("synonym")).named("synonym");
  //    Node aDataType =
  //        a.withProperties("property", Cypher.anonParameter("dataType")).named("dataType");
  //    Relationship cRel = cv.relationshipTo(c, "IS_VERSION_OF").named("cRel");
  //    NamedPath p1 = Cypher.path("p1").definedBy(cv.relationshipTo(a,
  // "HAS_ANNOTATION").unbounded());
  //    NamedPath p2 =
  //        Cypher.path("p2").definedBy(a.relationshipTo(Cypher.node("Class"), "HAS_CLASS_VALUE"));
  //
  //    Condition typeCondition = Conditions.noCondition();
  //    if (type != null) {
  //      for (EntityType t : type) {
  //        typeCondition = typeCondition.or(c.hasLabels(t.getValue()));
  //      }
  //    }
  //
  //    return Cypher.match(c.relationshipTo(cv, "CURRENT_VERSION"))
  //        .match(cRel)
  //        .match(r)
  //        .where(typeCondition)
  //        .and(
  //            primaryOnly
  //                ? r.property("id")
  //                    .isEqualTo(c.property("repositoryId"))
  //                    .and(r.property("primary").isEqualTo(Cypher.literalTrue()))
  //                : Cypher.literalTrue().asCondition())
  //        .and(
  //            repositoryId != null
  //                ? c.property("repositoryId").isEqualTo(Cypher.anonParameter(repositoryId))
  //                : Cypher.literalTrue().asCondition())
  //        .optionalMatch(cv.relationshipTo(aTitle, "HAS_ANNOTATION"))
  //        .optionalMatch(cv.relationshipTo(aSynonym, "HAS_ANNOTATION"))
  //        .optionalMatch(cv.relationshipTo(aDataType, "HAS_ANNOTATION"))
  //        .with(
  //            c.getRequiredSymbolicName(),
  //            cRel.getRequiredSymbolicName(),
  //            cv.getRequiredSymbolicName(),
  //            aTitle.getRequiredSymbolicName(),
  //            aSynonym.getRequiredSymbolicName(),
  //            aDataType.getRequiredSymbolicName())
  //        .where(Cypher.literalTrue().asCondition())
  //        .and(
  //            name != null
  //                ? Functions.toLower(aTitle.property("stringValue"))
  //                    .contains(Cypher.anonParameter(name.toLowerCase()))
  //                    .or(
  //                        Functions.toLower(aSynonym.property("stringValue"))
  //                            .contains(Cypher.anonParameter(name.toLowerCase())))
  //                : Cypher.literalTrue().asCondition())
  //        .and(
  //            dataType != null
  //                ? aDataType
  //                    .property("stringValue")
  //                    .isEqualTo(Cypher.anonParameter(dataType.getValue()))
  //                : Cypher.literalTrue().asCondition())
  //        .with(cv, Functions.collect(cRel).as("cRel"), Functions.collect(c).as("c"))
  //        .optionalMatch(p1)
  //        .optionalMatch(p2)
  //        .returning(
  //            cv.getRequiredSymbolicName(),
  //            cRel.getRequiredSymbolicName(),
  //            c.getRequiredSymbolicName(),
  //            Functions.collect(Functions.nodes(p1)),
  //            Functions.collect(Functions.relationships(p1)),
  //            Functions.collect(Functions.nodes(p2)),
  //            Functions.collect(Functions.relationships(p2)))
  //        .build();
  //  }

  @Override
  @Cacheable("entityCount")
  public long count() {
    return entityRepository.count();
  }

  @Cacheable("entityCount")
  public long count(EntityType... types) {
    return entityRepository.countByEntityType(types);
  }

  @Transactional
  @Caching(
      evict = {@CacheEvict("entityCount"), @CacheEvict(value = "entities", key = "#repositoryId")})
  public Entity createEntity(String organisationId, String repositoryId, Entity entity) {
    if (entityRepository.existsById(entity.getId()))
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    getRepository(organisationId, repositoryId);

    if (entity.getEntityType() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityType is missing");

    return entityRepository.save(entity);
  }

  @Transactional
  @Caching(
      evict = {@CacheEvict("entityCount"), @CacheEvict(value = "entities", key = "#repositoryId")})
  public List<Entity> createFork(
      String organisationId,
      String repositoryId,
      String id,
      ForkingInstruction forkingInstruction,
      Integer version,
      List<String> include) {
    if (repositoryId.equals(forkingInstruction.getRepositoryId()))
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE,
          String.format("Cannot create fork of entity '%s' in the same repository.", id));

    Repository originRepo = getRepository(organisationId, repositoryId);

    if (!originRepo.isPrimary())
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE,
          String.format(
              "Cannot create fork of entity '%s' from non-primary repository '%s'.",
              id, originRepo.getId()));

    Repository destinationRepo =
        getRepository(forkingInstruction.getOrganisationId(), forkingInstruction.getRepositoryId());

    Entity entity = loadEntity(organisationId, repositoryId, id, null);

    List<Entity> origins = new ArrayList<>();
    if (ApiModelMapper.isRestricted(entity)) {
      Entity superPhenotype =
          loadEntity(
              organisationId, repositoryId, ((Phenotype) entity).getSuperPhenotype().getId(), null);
      origins.add(superPhenotype);
      origins.add(entity);
    } else {
      origins.add(entity);
      if (forkingInstruction.isCascade())
        origins.addAll(getSubclasses(organisationId, repositoryId, origins.get(0).getId(), null));
    }

    List<Entity> results = new ArrayList<>();
    for (Entity origin : origins) {
      String oldId = origin.getId();
      Optional<Entity> fork = entityRepository.getFork(origin, destinationRepo);

      if (!forkingInstruction.isUpdate() && fork.isPresent()) continue;

      if (forkingInstruction.isUpdate() && fork.isPresent()) {
        if (entityRepository.equalCurrentVersions(fork.get().getId(), origin.getId())) continue;
        origin.setId(fork.get().getId());
        origin.setVersion(fork.get().getVersion() + 1);
        if (origin instanceof Phenotype) {
          ((Phenotype) origin).setSuperCategories(((Phenotype) fork.get()).getSuperCategories());
        } else if (origin instanceof Category) {
          ((Category) origin).setSuperCategories(((Category) fork.get()).getSuperCategories());
        }
      }

      if (!forkingInstruction.isUpdate() || fork.isEmpty()) {
        origin.setId(UUID.randomUUID().toString());
        origin.setVersion(1);
        if (origin instanceof Phenotype) ((Phenotype) origin).setSuperCategories(null);
        else if (origin instanceof Category) ((Category) origin).setSuperCategories(null);
      }

      if (origin instanceof Phenotype) {
        Phenotype phenotype = (Phenotype) origin;
        if (phenotype.getSuperPhenotype() != null) {
          Optional<Entity> superClass =
              entityRepository.getFork(phenotype.getSuperPhenotype(), destinationRepo);
          if (superClass.isEmpty()) continue;
          phenotype.setSuperPhenotype((Phenotype) new Phenotype().id(superClass.get().getId()));
        }
      }

      if (origin.getVersion() == 1) {
        results.add(
            createEntity(forkingInstruction.getOrganisationId(), destinationRepo.getId(), origin));
        entityRepository.setFork(origin.getId(), oldId);
      } else {
        results.add(
            updateEntityById(
                forkingInstruction.getOrganisationId(),
                destinationRepo.getId(),
                origin.getId(),
                origin,
                null));
      }

      Entity forkVersion = entityRepository.findCurrentById(origin.getId()).orElseThrow();
      entityRepository
          .findCurrentById(oldId)
          .ifPresent(e -> entityRepository.setEquivalentVersion(forkVersion, e));
    }

    return results;
  }

  @Transactional
  @Caching(
      evict = {@CacheEvict("entityCount"), @CacheEvict(value = "entities", key = "#repositoryId")})
  public void deleteEntity(String organisationId, String repositoryId, String id) {
    Repository repository = getRepository(organisationId, repositoryId);
    Entity entity =
        entityRepository
            .findById(id)
            .filter(e -> repository.getId().equals(e.getRepository().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    entityRepository.delete(entity);
  }

  @Transactional
  public void deleteVersion(
      String organisationId, String repositoryId, String id, Integer version) {
    Repository repository = getRepository(organisationId, repositoryId);
    Entity entity =
        entityRepository
            .findByIdAndRepositoryIdAndVersion(id, repository.getId(), version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    Entity currentVersion =
        entityRepository
            .findCurrentById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Class does not have a current version."));

    if (entity.equals(currentVersion))
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE, "Current version of a class cannot be deleted.");

    entityRepository
        .getNext(entity)
        .ifPresent(
            next ->
                entityRepository
                    .getPrevious(entity)
                    .ifPresent(prev -> entityRepository.setPreviousVersion(next, prev)));

    entityRepository.delete(entity);
  }

  public StringWriter exportEntity(
      String organisationId, String repositoryId, String id, String format, Integer version) {
    StringWriter writer = new StringWriter();
    Collection<Entity> entities =
        getEntitiesByRepositoryId(organisationId, repositoryId, null, null, null, null, null);
    if ("vnd.r-project.r".equals(format)) {
      Collection<Phenotype> phenotypes =
          entities.stream()
              .filter(e -> !EntityType.CATEGORY.equals(e.getEntityType()))
              .map(e -> (Phenotype) e)
              .collect(Collectors.toList());
      try {
        Phenotype2RConverter converter = new Phenotype2RConverter(phenotypes);
        Entity entity = loadEntity(organisationId, repositoryId, id, version);
        if (EntityType.CATEGORY.equals(entity.getEntityType())) {
          for (Entity subClass :
              getSubclasses(organisationId, repositoryId, id, null).stream()
                  .filter(e -> !EntityType.CATEGORY.equals(e.getEntityType()))
                  .collect(Collectors.toList())) {
            converter.convert(subClass.getId(), writer);
            writer.append(System.lineSeparator());
          }
        } else {
          converter.convert(id, writer);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
    }
    return writer;
  }

  public List<Entity> getEntities(
      List<String> include, String name, List<EntityType> type, DataType dataType, Integer page) {
    int requestedPage = page != null ? page - 1 : 0;
    return entityRepository
        .findAllByRepositoryIdAndNameAndEntityTypeAndDataTypeAndPrimary(
            null, name, type, dataType, true, PageRequest.of(requestedPage, pageSize))
        .parallelStream()
        .flatMap(
            entity -> {
              Stream<Entity> result = Stream.of(entity);
              if (type == null
                  || type.contains(ApiModelMapper.toRestrictedEntityType(entity.getEntityType())))
                result =
                    Stream.concat(result, entityRepository.findBySuperPhenotypeId(entity.getId()));
              return result;
            })
        .filter(distinctByKey(Entity::getId))
        .collect(Collectors.toList());
  }

  @Cacheable(
      value = "entities",
      key = "#repositoryId",
      condition = "#name == null && #type == null && #dataType == null")
  public List<Entity> getEntitiesByRepositoryId(
      String organisationId,
      String repositoryId,
      List<String> include,
      String name,
      List<EntityType> type,
      DataType dataType,
      Integer page) {
    getRepository(organisationId, repositoryId);
    int requestedPage = page != null ? page - 1 : 0;
    return entityRepository
        .findAllByRepositoryIdAndNameAndEntityTypeAndDataTypeAndPrimary(
            repositoryId, name, type, dataType, false, PageRequest.of(requestedPage, pageSize))
        .parallelStream()
        .flatMap(
            entity -> {
              Stream<Entity> result = Stream.of(entity);
              if (type == null
                  || type.contains(ApiModelMapper.toRestrictedEntityType(entity.getEntityType())))
                result =
                    Stream.concat(result, entityRepository.findBySuperPhenotypeId(entity.getId()));
              return result;
            })
        .filter(distinctByKey(Entity::getId))
        .collect(Collectors.toList());
  }

  public ForkingStats getForkingStats(
      String organisationId, String repositoryId, String id, List<String> include) {
    Repository repository = getRepository(organisationId, repositoryId);
    Entity entity =
        entityRepository
            .findById(id)
            .filter(e -> repository.getId().equals(e.getRepository().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    ForkingStats forkingStats = new ForkingStats();

    forkingStats.setForks(new ArrayList<>(entityRepository.getForks(entity.getId())));

    entityRepository.findOrigin(entity).ifPresent(forkingStats::origin);

    return forkingStats;
  }

  public List<Entity> getRestrictions(String ownerId, Phenotype abstractPhenotype) {
    if (!ApiModelMapper.isAbstract(abstractPhenotype)) return new ArrayList<>();
    return entityRepository
        .findBySuperPhenotypeId(abstractPhenotype.getId())
        .collect(Collectors.toList());
  }

  public List<Entity> getRootEntitiesByRepositoryId(
      String organisationId,
      String repositoryId,
      List<String> include,
      String name,
      List<EntityType> type,
      DataType dataType,
      Integer page) {
    getRepository(organisationId, repositoryId);
    return new ArrayList<>(
        entityRepository.findAllByRepositoryIdAndSuperPhenotypeId(repositoryId, null));
  }

  public List<Entity> getSubclasses(
      String organisationId, String repositoryId, String id, List<String> include) {
    getRepository(organisationId, repositoryId);
    return entityRepository.findBySuperPhenotypeId(id).collect(Collectors.toList());
  }

  public List<Entity> getVersions(
      String organisationId, String repositoryId, String id, List<String> include) {
    getRepository(organisationId, repositoryId);
    return new ArrayList<>(
        entityRepository.findAllById(id, PageRequest.of(0, 10, Sort.Direction.DESC, "version")));
  }

  public Entity loadEntity(String organisationId, String repositoryId, String id, Integer version) {
    Repository repository = getRepository(organisationId, repositoryId);
    return entityRepository
        .findByIdAndRepositoryIdAndVersion(id, repository.getId(), version)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @CacheEvict(value = "entities", key = "#repositoryId")
  public Entity setCurrentEntityVersion(
      String organisationId,
      String repositoryId,
      String id,
      Integer version,
      List<String> include) {
    Repository repository = getRepository(organisationId, repositoryId);
    Entity entity =
        entityRepository
            .findByIdAndRepositoryIdAndVersion(id, repository.getId(), version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    entityRepository.setCurrent(entity);

    return entity;
  }

  @CacheEvict(value = "entities", key = "#repositoryId")
  public Entity updateEntityById(
      String organisationId, String repositoryId, String id, Entity entity, List<String> include) {
    Repository repository = getRepository(organisationId, repositoryId);
    Entity oldEntity =
        entityRepository
            .findById(id)
            .filter(e -> repository.getId().equals(e.getRepository().getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (!Objects.equals(oldEntity.getId(), entity.getId())
        || oldEntity.getEntityType() != entity.getEntityType())
      throw new ResponseStatusException(HttpStatus.CONFLICT);

    entity.setVersion(entityRepository.getNextVersion(oldEntity));
    return entityRepository.save(entity);
  }

  /**
   * Get {@link Repository} by repositoryId and directoryId. If the repository does not exist or is
   * not associated with the directory, this method will throw an exception.
   *
   * @param organisationId ID of the {@link Organisation}
   * @param repositoryId ID of the {@link Repository}
   * @return The matching repository, if it exists.
   */
  private Repository getRepository(String organisationId, String repositoryId) {
    return repositoryService
        .getRepository(organisationId, repositoryId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format("Repository '%s' does not exist!", repositoryId)));
  }
}
