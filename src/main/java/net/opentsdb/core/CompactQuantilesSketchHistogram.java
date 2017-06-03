// This file is part of OpenTSDB.
// Copyright (C) 2017  The OpenTSDB Authors.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 2.1 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.
package net.opentsdb.core;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.yahoo.memory.NativeMemory;
import com.yahoo.sketches.quantiles.CompactDoublesSketch;
import com.yahoo.sketches.quantiles.DoublesUnion;

import net.opentsdb.core.Histogram;
import net.opentsdb.core.HistogramAggregation;

/**
 * A {@link Histogram} implementation using the Yahoo Quantile sketch. 
 * <p>
 * Because we currently treat histograms as immutable in OpenTSDB, this class 
 * expects that the source has finished compiling the quantile data for a period
 * of time and is sending in the immutable, compact form for storage.
 *  
 * @since 2.4
 */
public class CompactQuantilesSketchHistogram implements Histogram {

  /** The ID of this histogram in the TSD. */
  private final int id;
  
  /** The sketch for this data point. */
  private CompactDoublesSketch sketch;
  
  /** If downsampling or grouping was called for, this is set with the sum. */
  private DoublesUnion union;
  
  /**
   * Default ctor.
   * @param id The ID within the TSD.
   * @throws IllegalArgumentException if the ID was not a value from 0 to 255.
   */
  public CompactQuantilesSketchHistogram(final int id) {
    if (id < 0 || id > 255) {
      throw new IllegalArgumentException("ID must be between 0 and 255");
    }
    this.id = id;
  }
  
  public byte[] histogram(final boolean include_id) {
    if (sketch == null) {
      throw new IllegalStateException("The sketch has not been set yet.");
    }
    final byte[] encoded = sketch.toByteArray(true);
    if (include_id) {
      final byte[] with_id = new byte[encoded.length + 1];
      with_id[0] = (byte) id;
      System.arraycopy(encoded, 0, with_id, 1, encoded.length);
      return with_id;
    }
    return encoded;
  }

  public void fromHistogram(final byte[] raw, final boolean includes_id) {
    if (raw == null || raw.length < 8) {
      throw new IllegalArgumentException("Raw data cannot be null or less "
          + "than 8 bytes.");
    }
    if (includes_id && raw.length < 9) {
      throw new IllegalArgumentException("Must have more than 1 bytes.");
    }
    final byte[] encoded;
    if (includes_id) {
      encoded = new byte[raw.length - 1];
      System.arraycopy(raw, 1, encoded, 0, raw.length - 1);
    } else {
      encoded = raw;
    }
    sketch = CompactDoublesSketch.heapify(new NativeMemory(encoded));
  }

  public double percentile(double p) {
    // sketches expect percentiles to be < 1. e.g. 0.95 == 95%.
    return union != null ? union.getResult().getQuantile(p / 100) : 
      sketch.getQuantile(p / 100);
  }

  public List<Double> percentiles(List<Double> p) {
    final double[] percentiles = new double[p.size()];
    
    for (int i = 0; i < p.size(); i++) {
      percentiles[i] = p.get(i) / 100;
    }
    final double[] results = union != null ? 
        union.getResult().getQuantiles(percentiles) : 
          sketch.getQuantiles(percentiles);
    final List<Double> response = Lists.newArrayListWithCapacity(results.length);
    for (final double result : results) {
      response.add(result);
    }
    return response;
  }

  public Map getHistogram() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public Histogram clone() {
    final CompactQuantilesSketchHistogram clone = 
        new CompactQuantilesSketchHistogram(id);
    clone.fromHistogram(histogram(false), false);
    return clone;
  }

  public int getId() {
    return id;
  }

  public void aggregate(final Histogram histo, final HistogramAggregation func) {
    if (func != HistogramAggregation.SUM) {
      throw new UnsupportedOperationException("Function " + func 
          + " is not supported yet."); 
    }
    if (!(histo instanceof CompactQuantilesSketchHistogram)) {
      throw new IllegalArgumentException("Incoming histogram was not of the "
          + "same type: " + histo.getClass());
    }
    if (union == null) {
      union = DoublesUnion.builder().build();
      union.update(sketch);
    }
    union.update(((CompactQuantilesSketchHistogram) histo).sketch);
  }

  public void aggregate(final List<Histogram> histos, 
                        final HistogramAggregation func) {
    if (func != HistogramAggregation.SUM) {
      throw new UnsupportedOperationException("Function " + func 
          + " is not supported yet."); 
    }
    if (union == null) {
      union = DoublesUnion.builder().build();
      union.update(sketch);
    }
    for (final Histogram histogram : histos) {
      if (!(histogram instanceof CompactQuantilesSketchHistogram)) {
        throw new IllegalArgumentException("Incoming histogram was not of the "
            + "same type: " + histogram.getClass());
      }
      union.update(((CompactQuantilesSketchHistogram) histogram).sketch);
    }
  }

  /**
   * @param sketch The sketch to set for this histogram. <b>NOTE:</b> Resets the
   * union if present.
   */
  public void setSketch(final CompactDoublesSketch sketch) {
    this.sketch = sketch;
    union = null;
  }
  
  /** @return The sketch associated with this histogram. */
  public CompactDoublesSketch getSketch() {
    return sketch;
  }
}
