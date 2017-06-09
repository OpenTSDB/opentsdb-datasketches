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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.yahoo.sketches.SketchesArgumentException;
import com.yahoo.sketches.quantiles.CompactDoublesSketch;
import com.yahoo.sketches.quantiles.UpdateDoublesSketch;

public class TestCompactQuantilesSketchHistogram {
  
  private UpdateDoublesSketch sketch;
  
  @Before
  public void before() throws Exception {
    sketch = CompactDoublesSketch.builder().build();
    sketch.update(42.5);
    sketch.update(1);
    sketch.update(24.0);
  }

  @Test
  public void ctor() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    assertEquals(42, histo.getId());
    
    try {
      new CompactQuantilesSketchHistogram(-1);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      new CompactQuantilesSketchHistogram(256);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
  
  @Test
  public void histogram() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    
    assertArrayEquals(sketch.compact().toByteArray(), histo.histogram(false));
    byte[] with_id = histo.histogram(true);
    assertEquals(42, with_id[0]);
    byte[] raw = new byte[with_id.length - 1];
    System.arraycopy(with_id, 1, raw, 0, with_id.length - 1);
    assertArrayEquals(sketch.compact().toByteArray(), raw);
  }

  @Test
  public void fromHistogram() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.fromHistogram(sketch.compact().toByteArray(), false);
    assertEquals(42.5, histo.percentile(95.0), 0.01);
    
    byte[] raw = sketch.compact().toByteArray();
    byte[] with_id = new byte[raw.length + 1];
    with_id[0] = 42;
    System.arraycopy(raw, 0, with_id, 1, raw.length);
    
    histo.fromHistogram(with_id, true);
    assertEquals(42.5, histo.percentile(95.0), 0.01);
    
    try {
      histo.fromHistogram(new byte[] {42, 2, 4, 5, 1, 2, 8, 12, 90, 42 }, false);
      fail("Expected SketchesArgumentException");
    } catch (SketchesArgumentException e) { }
    
    try {
      histo.fromHistogram(null, false);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      histo.fromHistogram(new byte[0], false);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      histo.fromHistogram(new byte[1], true);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }

  @Test
  public void percentile() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    
    assertEquals(42.5, histo.percentile(95.0), 0.01);
    assertEquals(24.0, histo.percentile(50.0), 0.01);
    assertEquals(1.0, histo.percentile(0.0), 0.01);
    
    try {
      histo.percentile(-1);
      fail("Expected SketchesArgumentException");
    } catch (SketchesArgumentException e) { }
    
    try {
      histo.percentile(101);
      fail("Expected SketchesArgumentException");
    } catch (SketchesArgumentException e) { }
  }
  
  @Test
  public void percentiles() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    
    final List<Double> percentiles = 
        histo.percentiles(Lists.<Double>newArrayList(0D, 50D, 95.0D));
    assertEquals(3, percentiles.size());
    assertEquals(1.0, percentiles.get(0), 0.01);
    assertEquals(24.0, percentiles.get(1), 0.01);
    assertEquals(42.5, percentiles.get(2), 0.01);
    
    // wrong order
    try {
      histo.percentiles(Lists.<Double>newArrayList(95.D, 50D, 0.0D));
      fail("Expected SketchesArgumentException");
    } catch (SketchesArgumentException e) { }
    
    try {
      histo.percentiles(Lists.<Double>newArrayList(0D, -1D, 95.0D));
      fail("Expected SketchesArgumentException");
    } catch (SketchesArgumentException e) { }
  }
  
  @Test (expected = UnsupportedOperationException.class)
  public void getHistogram() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    histo.getHistogram();
  }
  
  @Test
  public void getClone() throws Exception {
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    
    final CompactQuantilesSketchHistogram copy = 
        (CompactQuantilesSketchHistogram) histo.clone();
    assertNotSame(histo, copy);
    assertNotSame(histo.getSketch(), copy.getSketch());
    assertEquals(42.5, histo.percentile(95.0), 0.01);
  }
  
  @Test
  public void aggregate() throws Exception {
    UpdateDoublesSketch sketch2 = CompactDoublesSketch.builder().build();
    sketch2.update(12);
    sketch2.update(89.3);
    sketch2.update(15);
    
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    
    final CompactQuantilesSketchHistogram histo2 = 
        new CompactQuantilesSketchHistogram(42);
    histo2.setSketch(sketch2.compact());
    
    histo.aggregate(histo2, HistogramAggregation.SUM);
    
    assertEquals(89.3, histo.percentile(95.0), 0.01);
    assertEquals(24.0, histo.percentile(50.0), 0.01);
    assertEquals(1.0, histo.percentile(0.0), 0.01);
    
    final SimpleHistogram simple = new SimpleHistogram(1);
    try {
      histo.aggregate(simple, HistogramAggregation.SUM);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
  
  @Test
  public void aggregateList() throws Exception {
    UpdateDoublesSketch sketch2 = CompactDoublesSketch.builder().build();
    sketch2.update(12);
    sketch2.update(89.3);
    sketch2.update(15);
    
    UpdateDoublesSketch sketch3 = CompactDoublesSketch.builder().build();
    sketch3.update(33);
    sketch3.update(22.4);
    sketch3.update(6.98);
    
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.setSketch(sketch.compact());
    
    final CompactQuantilesSketchHistogram histo2 = 
        new CompactQuantilesSketchHistogram(42);
    histo2.setSketch(sketch2.compact());
    
    final CompactQuantilesSketchHistogram histo3 = 
        new CompactQuantilesSketchHistogram(42);
    histo3.setSketch(sketch3.compact());
    
    histo.aggregate(Lists.<Histogram>newArrayList(histo2, histo3), 
        HistogramAggregation.SUM);
    
    assertEquals(89.3, histo.percentile(95.0), 0.01);
    assertEquals(22.4, histo.percentile(50.0), 0.01);
    assertEquals(1.0, histo.percentile(0.0), 0.01);
    
    final SimpleHistogram simple = new SimpleHistogram(1);
    try {
      histo.aggregate(simple, HistogramAggregation.SUM);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }

}
