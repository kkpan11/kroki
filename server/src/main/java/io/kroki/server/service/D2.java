package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.decode.SourceDecoder;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class D2 implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Collections.singletonList(FileFormat.SVG);
  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final Commander commander;

  private final Map<String, Integer> builtinThemes = Map.ofEntries(
    entry("default", 0),
    entry("neutral-gray", 1),
    entry("flagship-terrastruct", 3),
    entry("cool-classics", 4),
    entry("mixed-berry-blue", 5),
    entry("grape-soda", 6),
    entry("aubergine", 7),
    entry("colorblind-clear", 8),
    entry("vanilla-nitro-cola", 100),
    entry("orange-creamsicle", 101),
    entry("shirley-temple", 102),
    entry("earth-tones", 103),
    entry("everglade-green", 104),
    entry("buttered-toast", 105),
    entry("dark-mauve", 200),
    entry("dark-flagship-terrastruct", 201),
    entry("terminal", 300),
    entry("terminal-grayscale", 301),
    entry("origami", 302),
    entry("c4", 303)
  );

  public D2(Vertx vertx, JsonObject config, Commander commander) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_D2_BIN_PATH", "d2");
    this.sourceDecoder = new SourceDecoder() {
      @Override
      public String decode(String encoded) throws DecodeException {
        return DiagramSource.decode(encoded);
      }
    };
    this.commander = commander;
  }

  @Override
  public List<FileFormat> getSupportedFormats() {
    return SUPPORTED_FORMATS;
  }

  @Override
  public SourceDecoder getSourceDecoder() {
    return sourceDecoder;
  }

  @Override
  public String getVersion() {
    return "0.7.0";
  }

  @Override
  public void convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options, Handler<AsyncResult<Buffer>> handler) {
    vertx.executeBlocking(future -> {
      try {
        byte[] result = d2(sourceDecoded.getBytes(), options);
        future.complete(result);
      } catch (IOException | InterruptedException | IllegalStateException e) {
        future.fail(e);
      }
    }, res -> handler.handle(res.map(o -> Buffer.buffer((byte[]) o))));
  }

  private byte[] d2(byte[] source, JsonObject options) throws IOException, InterruptedException, IllegalStateException {
    List<String> commands = new ArrayList<>();
    commands.add(binPath);
    String layout = options.getString("layout");
    if (layout != null && layout.equals("elk")) {
      // Only pass the layout argument if the ELK layout engine is requested (default is 'dagre')
      commands.add("--layout=" + layout);
    }
    String theme = options.getString("theme");
    if (theme != null) {
      int themeId = 0;
      Integer builtinThemeId = builtinThemes.get(theme.toLowerCase().replaceAll("\\s", "-"));
      if (builtinThemeId != null) {
        themeId = builtinThemeId;
      } else {
        try {
          themeId = Integer.parseInt(theme, 10);
        } catch (NumberFormatException e) {
          // ignore, fallback to 0
        }
      }
      commands.add("--theme=" + themeId);
    }
    String darkTheme = options.getString("dark-theme");
    if (darkTheme != null) {
      int themeId = 0;
      Integer builtinThemeId = builtinThemes.get(darkTheme.toLowerCase().replaceAll("\\s", "-"));
      if (builtinThemeId != null) {
        themeId = builtinThemeId;
      } else {
        try {
          themeId = Integer.parseInt(darkTheme, 10);
        } catch (NumberFormatException e) {
          // ignore, fallback to 0
        }
      }
      commands.add("--dark-theme=" + themeId);
    }
    String pad = options.getString("pad");
    if (pad != null) {
      commands.add("--pad=" + pad);
    }
    String animateInterval = options.getString("animate-interval");
    if (animateInterval != null) {
      commands.add("--animate-interval=" + animateInterval);
    }
    String sketch = options.getString("sketch");
    if (sketch != null) {
      commands.add("--sketch");
    }
    String scale = options.getString("scale");
    if (scale != null) {
      commands.add("--scale=" + scale);
    }
    commands.add("-"); // read from stdin
    return commander.execute(source, commands.toArray(new String[0]));
  }
}
