package com.redis.om.vss.celebs;

import com.google.common.io.Files;
import com.redis.om.spring.annotations.EnableRedisEnhancedRepositories;
import com.redis.om.vss.celebs.domain.Celebrity;
import com.redis.om.vss.celebs.repositories.CelebrityRepository;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableRedisEnhancedRepositories(basePackages = "com.redis.om.vss.*")
public class VssCelebsApplication {

	Logger logger = LoggerFactory.getLogger(VssCelebsApplication.class);

	@Value("${com.redis.om.vss.maxRecords}") private long maxRecords;

	@Bean CommandLineRunner loadAndVectorizeProductData(CelebrityRepository repository,
			@Value("classpath:/data/celeb_faces.csv") File dataFile) {
		return args -> {
			if (repository.count() == 0) {
				logger.info("⚙️ Loading celebrities...");
				List<Celebrity> data = Files //
						.readLines(dataFile, StandardCharsets.UTF_8) //
						.stream() //
						.skip(1) //
						.limit(maxRecords) //
						.map(Celebrity::fromCSV) //
						.toList();

				ListUtils.partition(data.stream().collect(Collectors.toList()), 500).forEach(batch -> {
					repository.saveAll(batch);
				});
			}
			logger.info("🏁 {} Celebrities Available...", repository.count());
		};
	}
	public static void main(String[] args) {
		SpringApplication.run(VssCelebsApplication.class, args);
	}



}
