       ___                 _____ ____  ____  ____
      / _ \ _ __   ___ _ _|_   _/ ___||  _ \| __ )
     | | | | '_ \ / _ \ '_ \| | \___ \| | | |  _ \
     | |_| | |_) |  __/ | | | |  ___) | |_| | |_) |
      \___/| .__/ \___|_| |_|_| |____/|____/|____/
           |_|    The modern time series database.

[![Build Status](https://travis-ci.org/OpenTSDB/opentsdb-datasketches.svg?branch=master)](https://travis-ci.org/OpenTSDB/opentsdb-datasketches) [![Coverage Status](https://coveralls.io/repos/github/OpenTSDB/opentsdb-datasketches/badge.svg?branch=master)](https://coveralls.io/github/OpenTSDB/opentsdb-datasketches?branch=master)

# DataSketches

This plugin is used for storing and querying [Yahoo's Data Sketches] (https://datasketches.github.io/) as histograms. With [OpenTSDB] (http://opentsdb.net/). Sketches allow for collecting measurements (e.g. operation latency) from many systems, merging the results, then calculating accurate quantiles from the results with a fixed error bound. 

## Installation

1. Download the source code and run ``mvn package`` to create the jar in the ``target/`` directory. Copy this file to your OpenTSDB plugin directory as defined in the opentsdb config via ``tsd.core.plugin_path``.
1. Add the appropriate codec class to the ``tsd.core.histograms.config`` config in ``opentsdb.conf``. E.g. ``
{
  "net.opentsdb.core.CompactQuantilesSketchCodec":1
}``
1. Restart the TSD and make sure the plugin was loaded and associated with the proper ID. E.g. look in the logs for lines like:

```
2017-06-03 16:26:55,044 DEBUG [main] PluginLoader: Successfully added JAR to class loader: /Users/clarsen/Documents/opentsdb/plugins/opentsdb-datasketches-2.4.0-SNAPSHOT.jar
2017-06-03 16:26:55,550 INFO  [main] HistogramCodecManager: Successfully loaded decoder 'net.opentsdb.core.CompactQuantilesSketchCodec' with ID 1

```

## Usage
Currently sketches are implemented in Java so here's an example of how to create sketches and send them to OpenTSDB. First, import the latest ``sketches-core`` jar for your project, e.g.

```xml
<dependency>
  <groupId>com.yahoo.datasketches</groupId>
  <artifactId>sketches-core</artifactId>
  <version>0.9.1</version>
</dependency>
```

```java
import com.yahoo.sketches.quantiles.CompactDoublesSketch;
import com.yahoo.sketches.quantiles.UpdateDoublesSketch;
import javax.xml.bind.DatatypeConverter;

class SketchExample {
  private UpdateDoublesSketch latency =
     CompactDoublesSketch.builder().build();
  
  public updateAndEncode() {
    sketch.update(42.5);
    sketch.update(1);
    sketch.update(24.0);
  
    final byte[] encoded = sketch.compact().toByteArray();
    final String b64 = DatatypeConverter.printBase64Binary(encoded);
    
    // Send the b64 encoded string to the TSD via HTTP or Telnet.
  }
}
```

This is an extremely contrived example to start playing with sketches. In reality what you'll want to do is create a new sketch on a given interval, for example every 60 seconds, update that sketch with all of the measurements from your application, then at the end of 60 seconds, flush the sketch to TSD and start a new one. 

**NOTE:** Do not continue updating the same sketch and send it over and over again to TSD or your results will be incorrect when querying. Each sketch is supposed to be a snapshot over a time period.

### HTTP

To send the sketch over HTTP, create a JSON object like the following:

```javascript
{
  "metric": "webserver.request.latency.ms",
  "timestamp": 1346846400,
  "id":1,
  "value":"AgMIGoAAAAADAAAAAAAAAAAAAAAAAPA/AAAAAABARUAAAAAAAADwPwAAAAAAADhAAAAAAABARUA=",
  "tags": {
    "host": "web01"
  }
}
```

and ``POST`` it to ``<tsd>:4242/api/histogram``. The ``value`` is the base 64 encoded value from the exampl eabove.

### Telnet

Similar to HTTP, encode the sketch as a base 64 string and call:

``histogram webserver.request.latency.ms 1346846400  1 AgMIGoAAAAADAAAAAAAAAAAAAAAAAPA/AAAAAABARUAAAAAAAADwPwAAAAAAADhAAAAAAABARUA= host=web01``