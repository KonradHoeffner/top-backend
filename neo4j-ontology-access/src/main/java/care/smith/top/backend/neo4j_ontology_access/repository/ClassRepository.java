package care.smith.top.backend.neo4j_ontology_access.repository;

import care.smith.top.backend.neo4j_ontology_access.model.Class;
import care.smith.top.backend.neo4j_ontology_access.model.ClassVersion;
import care.smith.top.backend.neo4j_ontology_access.model.Repository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@org.springframework.stereotype.Repository
public interface ClassRepository extends PagingAndSortingRepository<Class, String> {
  @Query(
      "MATCH (super:Class {id: $classId}) <-[:IS_SUBCLASS_OF { ownerId: $repositoryId }]- (sub:Class) "
          + "RETURN sub ORDER BY sub.index")
  Stream<Class> findSubclasses(
          @Param("classId") String id, @Param("repositoryId") String repositoryId);

  @Query(
      "MATCH (c:Class { repositoryId: $repository.__id__ }) "
          + "WHERE NOT (c) -[:IS_SUBCLASS_OF]-> () "
          + "RETURN c")
  Set<Class> findRootClassesByRepository(@Param("repository") Repository repository);

  Optional<Class> findByIdAndRepositoryId(String id, String repositoryId);

  /**
   * Get a new version number for the given {@link Class} object. The returned number is the highest
   * available version number of this class incremented by 1. If the class does not exist or has no
   * version, this method returns 1.
   *
   * @param cls The {@link Class} object.
   * @return A version number to be used for a new {@link ClassVersion}.
   */
  @Query(
      "OPTIONAL MATCH (c:Class { id: $cls.__id__ }) "
          + "OPTIONAL MATCH (c) <-[:IS_VERSION_OF]- (cv:ClassVersion) "
          + "WITH max(cv.version) AS version "
          + "RETURN CASE version WHEN NULL THEN 0 ELSE version END + 1")
  Integer getNextVersion(@Param("cls") Class cls);

  @Query(
      "MATCH (c:Class { id: $cls.__id__ }) "
          + "MATCH (cv:ClassVersion) "
          + "WHERE cv.hiddenAt IS NULL AND id(cv) = $classVersion.__id__ "
          + "OPTIONAL MATCH (c) -[rel:CURRENT_VERSION]-> (:ClassVersion) "
          + "DELETE rel "
          + "CREATE (c) -[:CURRENT_VERSION]-> (cv) ")
  void setCurrent(@Param("cls") Class cls, @Param("classVersion") ClassVersion classVersion);
}
