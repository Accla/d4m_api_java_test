package edu.mit.ll.graphulo.skvi;

import edu.mit.ll.graphulo.util.PeekingIterator1;
import org.apache.accumulo.core.data.*;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;

import org.apache.hadoop.io.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * For testing; do not use.
 * Demonstrates how BatchScans return duplicate entries when this return entries past the seek() range fence.
 */
@Deprecated
public class BadHardListIterator implements SortedKeyValueIterator<Key, Value> {
  final static SortedMap<Key, Value> allEntriesToInject;

  static {
    SortedMap<Key, Value> t = new TreeMap<>();
    t.put(new Key("a1", "colF3", "colQ3"),
        new Value("1".getBytes(StandardCharsets.UTF_8)));
    t.put(new Key("c1", "colF3", "colQ3"),
        new Value("1".getBytes(StandardCharsets.UTF_8)));
    t.put(new Key("m1", "colF3", "colQ3"),
        new Value("1".getBytes(StandardCharsets.UTF_8)));
    allEntriesToInject = Collections.unmodifiableSortedMap(t); // for safety
  }

  private PeekingIterator1<Map.Entry<Key, Value>> inner;// = map.entrySet();

  @Override
  public void init(SortedKeyValueIterator<Key, Value> source, Map<String, String> options, IteratorEnvironment env) throws IOException {
    if (source != null)
      throw new IllegalArgumentException("HardListIterator does not take a parent source");
    // define behavior before seek as seek to start at negative infinity
    inner = new PeekingIterator1<>(allEntriesToInject.entrySet().iterator());
  }

  @Override
  public SortedKeyValueIterator<Key, Value> deepCopy(IteratorEnvironment env) {
    BadHardListIterator newInstance;
    try {
      newInstance = BadHardListIterator.class.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    newInstance.inner = new PeekingIterator1<>(allEntriesToInject.tailMap(inner.peek().getKey()).entrySet().iterator());

    return newInstance;
  }

  @Override
  public boolean hasTop() {
    return inner.hasNext();
  }

  @Override
  public void next() throws IOException {
    inner.next();
  }

  @Override
  public void seek(Range range, Collection<ByteSequence> columnFamilies, boolean inclusive) throws IOException {
    // seek to first entry inside range
    if (range.isInfiniteStartKey())
      inner = new PeekingIterator1<>(allEntriesToInject.entrySet().iterator());
    else if (range.isStartKeyInclusive())
      inner = new PeekingIterator1<>(allEntriesToInject.tailMap(range.getStartKey()).entrySet().iterator());
    else
      inner = new PeekingIterator1<>(allEntriesToInject.tailMap(range.getStartKey().followingKey(PartialKey.ROW_COLFAM_COLQUAL_COLVIS_TIME)).entrySet().iterator());
  }

  @Override
  public Key getTopKey() {
    return inner.peek().getKey();
  }

  @Override
  public Value getTopValue() {
    return inner.peek().getValue();
  }
}
