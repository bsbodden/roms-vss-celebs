package com.redis.om.vss.celebs.domain;

import com.google.gson.Gson;
import com.redis.om.spring.DistanceMetric;
import com.redis.om.spring.VectorType;
import com.redis.om.spring.annotations.EmbeddingType;
import com.redis.om.spring.annotations.Indexed;
import com.redis.om.spring.annotations.SchemaFieldType;
import com.redis.om.spring.annotations.Vectorize;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

@Data
@RequiredArgsConstructor(staticName = "of")
@NoArgsConstructor
@RedisHash
public class Celebrity {
  @Id
  private String id;

  @Indexed
  @NonNull
  private String name;

  @Indexed
  @NonNull
  private Double popularity;

  @Vectorize(destination = "imageEmbedding", embeddingType = EmbeddingType.FACE)
  @NonNull
  private String imageResource;

  @Indexed(//
    schemaFieldType = SchemaFieldType.VECTOR, //
    algorithm = VectorAlgorithm.HNSW, //
    type = VectorType.FLOAT32, //
    dimension = 512, //
    distanceMetric = DistanceMetric.L2, //
    initialCapacity = 10
  )
  private byte[] imageEmbedding;

  private String imdbId;

  private static Gson gson = new Gson();
  public static Celebrity fromCSV(String line) {
    // CSV columns
    // id,imdb_id,name,popularity,image_resource
    String[] values = line.split(",");
    String id = values[0];
    String imdbId = values[1];
    String name = values[2];
    Double popularity = Double.parseDouble(values[3]);
    String imageResource = values[4];

    Celebrity celebrity = Celebrity.of(name, popularity, imageResource);
    celebrity.setId(id);
    celebrity.setImdbId(imdbId);

    return celebrity;
  }

  public String getImage() {
    return StringUtils.removeStart(this.imageResource, "classpath:/static");
  }

  public String getUrl() { return String.format("https://www.imdb.com/name/%s", imdbId); }
}
