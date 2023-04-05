package com.redis.om.vss.celebs.controllers;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.DetectedObjects.DetectedObject;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import com.redis.om.spring.search.stream.EntityStream;
import com.redis.om.spring.tuple.Fields;
import com.redis.om.spring.tuple.Pair;
import com.redis.om.vss.celebs.domain.Celebrity;
import com.redis.om.vss.celebs.domain.Celebrity$;
import com.redis.om.vss.celebs.repositories.CelebrityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.redis.om.spring.util.ObjectUtils.floatArrayToByteArray;

@Controller
@RequestMapping("/")
public class CelebrityController {
  Logger logger = LoggerFactory.getLogger(CelebrityController.class);

  private static final int K = 6;

  @Autowired
  public ZooModel<Image, DetectedObjects> faceDetectionModel;

  @Autowired
  public ZooModel<Image, float[]> faceEmbeddingModel;

  @Autowired
  private CelebrityRepository repository;

  @Autowired
  private EntityStream entityStream;

  @GetMapping
  public String index(Model model) {
    logger.info("🔎 index ... K={}", K);

    return "index";
  }

  @PostMapping("/matches")
  public String handleFileUpload(
      @RequestParam("file") MultipartFile file,
      RedirectAttributes redirectAttributes,
      Model model) throws IOException, TranslateException {

    Image img = ImageFactory.getInstance().fromInputStream(file.getInputStream());

    try (Predictor<Image, DetectedObjects> faceDetector = faceDetectionModel.newPredictor();
        Predictor<Image, float[]> predictor = faceEmbeddingModel.newPredictor()) {
      DetectedObjects detection = faceDetector.predict(img);

      Optional<DetectedObject> maybeFace = IntStream.range(0, detection.getNumberOfObjects())
          .mapToObj(i -> (DetectedObject)detection.item(i)) //
          .filter(detectedObject -> detectedObject.getClassName().equals("Face")) //
          .findFirst();

      if (maybeFace.isPresent()) {
        DetectedObject face = maybeFace.get();
        Image faceImg = getSubImage(img, face.getBoundingBox());
        logger.info("😁️ :: Face Detected :: w → {}, h → {}", faceImg.getWidth(), faceImg.getHeight());

        float[] embedding = predictor.predict(faceImg);
        byte[] embeddingAsByteArray = floatArrayToByteArray(embedding);

        List<Pair<Celebrity,Double>> matchesWithScores = entityStream.of(Celebrity.class) //
          .filter(Celebrity$.IMAGE_EMBEDDING.knn(K, embeddingAsByteArray)) //
          .sorted(Celebrity$._IMAGE_EMBEDDING_SCORE) //
          .limit(K) //
          .map(Fields.of(Celebrity$._THIS, Celebrity$._IMAGE_EMBEDDING_SCORE)) //
          .collect(Collectors.toList());

        List<Celebrity> celebrities = matchesWithScores.stream().map(Pair::getFirst).toList();
        List<Double> scores = matchesWithScores.stream().map(Pair::getSecond).map(d -> 100.0 * (1 - d/2)).toList();

        logger.info("😁️ :: Found {} matching celebrities", celebrities.size());

        celebrities.stream().map(Celebrity::getName).forEach(n -> logger.info(n));

        model.addAttribute("celebrities", celebrities);
        model.addAttribute("scores", scores);
      }
    }

    return "fragments :: matches";
  }

  @GetMapping("/match/{id}")
  public String matchById(Model model, @PathVariable("id") String id) {
    logger.info("🔎 match by id: {}", id);

    Optional<Celebrity> maybeCelebrity = repository.findById(id);

    if (maybeCelebrity.isPresent()) {
      logger.info("🔎 Looking for celebrities that look like {}", maybeCelebrity.get().getName());
      List<Pair<Celebrity,Double>> matchesWithScores = entityStream.of(Celebrity.class) //
          .filter(Celebrity$.IMAGE_EMBEDDING.knn(K, maybeCelebrity.get().getImageEmbedding())) //
          .sorted(Celebrity$._IMAGE_EMBEDDING_SCORE) //
          .limit(K) //
          .map(Fields.of(Celebrity$._THIS, Celebrity$._IMAGE_EMBEDDING_SCORE)) //
          .collect(Collectors.toList());

      List<Celebrity> celebrities = matchesWithScores.stream().map(Pair::getFirst).toList();
      List<Double> scores = matchesWithScores.stream().map(Pair::getSecond).map(d -> 100.0 * (1 - d/2)).toList();

      celebrities.stream().map(Celebrity::getName).forEach(n -> logger.info(n));

      model.addAttribute("celebrities", celebrities);
      model.addAttribute("scores", scores);
    }

    return "index";
  }

  private static Image getSubImage(Image img, BoundingBox box) {
    Rectangle rect = box.getBounds();
    double[] extended = extendRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    int width = img.getWidth();
    int height = img.getHeight();
    int[] recovered = {
        (int) (extended[0] * width),
        (int) (extended[1] * height),
        (int) (extended[2] * width),
        (int) (extended[3] * height)
    };
    return img.getSubImage(recovered[0], recovered[1], recovered[2], recovered[3]);
  }

  private static double[] extendRect(double xmin, double ymin, double width, double height) {
    double centerx = xmin + width / 2;
    double centery = ymin + height / 2;
    if (width > height) {
      width += height * 2.0;
      height *= 3.0;
    } else {
      height += width * 2.0;
      width *= 3.0;
    }
    double newX = centerx - width / 2 < 0 ? 0 : centerx - width / 2;
    double newY = centery - height / 2 < 0 ? 0 : centery - height / 2;
    double newWidth = newX + width > 1 ? 1 - newX : width;
    double newHeight = newY + height > 1 ? 1 - newY : height;
    return new double[] {newX, newY, newWidth, newHeight};
  }

}
