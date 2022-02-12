/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.postgres;

import io.airbyte.db.jdbc.JdbcDatabase;
import io.airbyte.integrations.destination.jdbc.DataAdapter;
import io.airbyte.integrations.destination.jdbc.JdbcSqlOperations;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

public class PostgresSqlOperations extends JdbcSqlOperations {

  @Override
  public void insertRecordsInternal(final JdbcDatabase database,
                                    final List<AirbyteRecordMessage> records,
                                    final String schemaName,
                                    final String tmpTableName)
      throws SQLException {
    if (records.isEmpty()) {
      return;
    }

    database.execute(connection -> {
      File tmpFile = null;
      try {
        tmpFile = Files.createTempFile(tmpTableName + "-", ".tmp").toFile();
        writeBatchToFile(tmpFile, records);

        final var copyManager = new CopyManager(connection.unwrap(BaseConnection.class));
        final var sql = String.format("COPY %s.%s FROM stdin DELIMITER ',' CSV", schemaName, tmpTableName);
        final var bufferedReader = new BufferedReader(new FileReader(tmpFile));
        copyManager.copyIn(sql, bufferedReader);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      } finally {
        try {
          if (tmpFile != null) {
            Files.delete(tmpFile.toPath());
          }
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected Optional<DataAdapter> getDataAdapter() {
    return Optional.of(new PostgresDataAdapter());
  }

}
