package com.skillstorm.repositories;

import com.skillstorm.entities.Form;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import java.util.UUID;

@Repository
public interface FormRepository extends ReactiveCassandraRepository<Form, UUID> {

    // Find Form by UUID:
    // Note: Necessary because we introduced 2nd primary key column to cluster by username
    @Override
    @NonNull
    @Query("SELECT * FROM form WHERE id = ?0")
    Mono<Form> findById(@NonNull UUID id);

    // Find all active Forms by Username:
    @Query("SELECT * FROM form WHERE username = ?0 ALLOW FILTERING")
    Flux<Form> findAllFormsByUsername(String username);

    // Delete form by id:
    @Override
    @NonNull
    @Query("DELETE FROM form WHERE id = ?0")
    Mono<Void> deleteById(@NonNull UUID id);
}
