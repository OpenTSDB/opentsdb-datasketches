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
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.yahoo.sketches.quantiles.CompactDoublesSketch;
import com.yahoo.sketches.quantiles.UpdateDoublesSketch;

public class TestCompactQuantilesSketchCodec {

  private UpdateDoublesSketch sketch;
  
  @Before
  public void before() throws Exception {
    sketch = CompactDoublesSketch.builder().build();
    sketch.update(42.5);
    sketch.update(1);
    sketch.update(24.0);
  }
  
  @Test
  public void decode() throws Exception {
    final CompactQuantilesSketchCodec codec = new CompactQuantilesSketchCodec();
    codec.setId(42);
    
    final byte[] raw = sketch.compact().toByteArray();
    Histogram histo = codec.decode(raw, false);
    assertArrayEquals(raw, histo.histogram(false));
    assertEquals(42.5, histo.percentile(95.0), 0.01);
    
    byte[] with_id = new byte[raw.length + 1];
    with_id[0] = 42;
    System.arraycopy(raw, 0, with_id, 1, raw.length);
    histo = codec.decode(raw, false);
    assertArrayEquals(raw, histo.histogram(false));
    assertArrayEquals(with_id, histo.histogram(true));
    assertEquals(42.5, histo.percentile(95.0), 0.01);
    
    try {
      codec.decode(null, false);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      codec.decode(new byte[0], false);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
  
  @Test
  public void encode() throws Exception {
    final CompactQuantilesSketchCodec codec = new CompactQuantilesSketchCodec();
    codec.setId(42);
    
    final byte[] raw = sketch.compact().toByteArray();
    final CompactQuantilesSketchHistogram histo = 
        new CompactQuantilesSketchHistogram(42);
    histo.fromHistogram(sketch.compact().toByteArray(), false);
    
    assertArrayEquals(raw, codec.encode(histo, false));
    
    byte[] with_id = codec.encode(histo, true);
    assertEquals(42, with_id[0]);
    byte[] raw_component = new byte[raw.length];
    System.arraycopy(with_id, 1, raw_component, 0, raw.length);
    assertArrayEquals(raw, raw_component);
    
    try {
      codec.encode(null, false);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
}
