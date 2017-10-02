/* Copyright (c) 2014-2015 Runtime Verification Inc. All Rights Reserved. */

package jtsan;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;


/**
 * Class containing tests of proper mocking of the Java Collection Framework.
 *
 * @author YilongL
 */
public class JUCollectionTests {

    //------------------ Positive tests ---------------------

    @RaceTest(expectRace = true,
            description = "Basic operations in Collection interface")
    public void basicCollectionOps() {
        final Collection<Integer> collection = new ArrayList<>();

        new ThreadRunner(2) {
            @Override
            public void thread1() {
                collection.add(1);
            }

            @Override
            public void thread2() {
                collection.add(0);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "one thread iterating over Iterable using for-each loop; another thread writes")
    public void foreachLoop0() {
        final Collection<Integer> iterable = new ArrayList<>();

        new ThreadRunner(2) {

            @Override
            public void setUp() {
                for (int i = 0; i < 10; i++) {
                    iterable.add(i);
                }
            }

            @Override
            public void thread1() {
                shortSleep(); // avoid ConcurrentModificationException
                int sum = 0;
                for (int i : iterable) {
                    sum += i;
                }
            }

            @Override
            public void thread2() {
                iterable.add(0);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "two different iterators")
    public void foreachLoop1() {
        final Collection<Integer> iterable = new ArrayList<>();

        new ThreadRunner(2) {

            @Override
            public void setUp() {
                for (int i = 0; i < 10; i++) {
                    iterable.add(i);
                }
            }

            @Override
            public void thread1() {
                shortSleep(); // avoid ConcurrentModificationException
                int sum = 0;
                for (int i : iterable) {
                    sum += i;
                }
            }

            @Override
            public void thread2() {
                Iterator<Integer> iter = iterable.iterator();
                if (iter.hasNext()) {
                    iter.next();
                    iter.remove();
                }
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "two threads iterating over an Iterable")
    public void readOnlyIteration() {
        final Collection<Integer> iterable = new ArrayList<>();

        new ThreadRunner(2) {

            @Override
            public void setUp() {
                for (int i = 0; i < 10; i++) {
                    iterable.add(i);
                }
            }

            @Override
            public void thread1() {
                int sum = 0;
                for (int i : iterable) { sum += i; }
            }

            @Override
            public void thread2() {
                int sum = 0;
                for (int i : iterable) { sum += i; }
            }
        };
    }

    private static class DelegatedIterator implements Iterator<Integer> {

        private final Iterator<Integer> iter;

        private DelegatedIterator(Collection<Integer> collection) {
            iter = collection.iterator();
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Integer next() {
            return iter.next() * 2;
        }
    }

    @RaceTest(expectRace = true,
            description = "customized implementation of iterator by delegation")
    public void delegatedIterator() {
        final Collection<Integer> ints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ints.add(i);
        }

        new ThreadRunner(2) {

            @Override
            public void thread1() {
                shortSleep();
                DelegatedIterator iter = new DelegatedIterator(ints);
                while (iter.hasNext()) {
                    iter.next();
                }
            }

            @Override
            public void thread2() {
                ints.add(0);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "Basic operations in Map interface")
    public void basicMapOps() {
        final Map<Integer, Integer> map = new HashMap<>();

        new ThreadRunner(2) {
            @Override
            public void thread1() {
                map.get(0);
            }

            @Override
            public void thread2() {
                map.put(0, 0);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "modifying collection views of a map")
    public void collectionViewsOfMap() {
        final Map<Integer, Integer> map = new HashMap<>();

        new ThreadRunner(2) {

            @Override
            public void setUp() {
                for (int i = 0; i < 10; i++) {
                    map.put(i, i);
                }
            }

            @Override
            public void thread1() {
                shortSleep();
                Set<Integer> keySet = map.keySet();
                for (Integer key : keySet) {};  // access key set view via iterator
            }

            @Override
            public void thread2() {
                map.put(3, 3);
            }
        };
    }

    @ExcludedTest(reason = "not worth to mock Map.Entry to detect race on each separate entry")
    @RaceTest(expectRace = true,
            description = "Test mocking of map entry")
    public void mapEntry() {
        new ThreadRunner(2) {

            Entry<Integer, Integer> entry;

            @Override
            public void setUp() {
                Map<Integer, Integer> m = new HashMap<>();
                m.put(0, 0);
                entry = m.entrySet().iterator().next();
            }

            @Override
            public void thread1() {
                entry.getValue();
            }

            @Override
            public void thread2() {
                entry.setValue(1);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "two threads access through two different synchronized views")
    public void differentSynchronizedViews() {
        final Collection<Integer> c = new ArrayList<>();
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                Collections.synchronizedCollection(c).add(1);
            }

            @Override
            public void thread2() {
                Collections.synchronizedCollection(c).contains(1);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "iterate over synchronized collection without manually synchronize on it")
    public void iterateSyncCollectionWrong() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(0);
        a.add(1);
        final Collection<Integer> c = Collections.synchronizedCollection(a);

        new ThreadRunner(2) {

            @Override
            public void thread1() {
                c.iterator().hasNext();
            }

            @Override
            public void thread2() {
                c.add(2);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "iterate over the collection views of a synchronized map without correct synchronization")
    public void syncMapIterateCollectionViewWrong() {
        final Map<Integer, Integer> m = Collections.synchronizedMap(new HashMap<Integer, Integer>());

        new ThreadRunner(2) {

            Set<Integer> keySet;

            @Override
            public void setUp() {
                keySet = m.keySet();
            }

            @Override
            public void thread1() {
                synchronized (keySet) { keySet.iterator().hasNext(); } // should sync on m not keySet
            }

            @Override
            public void thread2() {
                m.put(2, 2);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "list iterator")
    public void listIterator() {
        List<Integer> list = new ArrayList<>();
        list.add(1);

        new ThreadRunner(2) {

            @Override
            public void thread1() {
                list.listIterator(0).hasPrevious();
            }

            @Override
            public void thread2() {
                list.add(2);
            }
        };
    }

    @ExcludedTest(reason = "cannot intercept constructor")
    @RaceTest(expectRace = true,
            description = "test instrumentation of conversion constructor")
    public void conversionCtor() {
        final Map<Integer, Integer> m = new HashMap<>();

        new ThreadRunner(2) {

            @Override
            public void thread1() {
                new ArrayList<>(m.keySet());
                new LinkedList<>(m.entrySet());
                new HashSet<>(m.values());
                new HashMap<>(m);
                new TreeMap<>(m);
            }

            @Override
            public void thread2() {
                m.put(2, 2);
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Test instrumentation of Collections$SynchronizedX classes")
    public void synchronizedCollections() {
        final Collection<Integer> c = Collections.synchronizedCollection(new ArrayList<Integer>());
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                c.add(1);
            }

            @Override
            public void thread2() {
                c.contains(1);
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "iterate over the collection views of a synchronized map with proper synchronization")
    public void syncMapIterateCollectionView() {
        final Map<Integer, Integer> m = Collections.synchronizedMap(new HashMap<Integer, Integer>());

        new ThreadRunner(2) {

            @Override
            public void thread1() {
                synchronized (m) { m.keySet().iterator().hasNext(); }
                synchronized (m) { m.values().iterator().hasNext(); }
                synchronized (m) { m.entrySet().iterator().hasNext(); }
            }

            @Override
            public void thread2() {
                m.put(2, 2);
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "test List-specific add/remove methods")
    public void syncListAddAndRemove() {
        new ThreadRunner(2) {
            List<Integer> l;

            @Override
            public void setUp() {
                l = Collections.synchronizedList(new ArrayList<>());
            }

            @Override
            public void thread1() {
                sharedVar = 0;
                l.add(0, 0);
            }

            @Override
            public void thread2() {
                while (true) {
                    try {
                        l.remove(0);
                        sharedVar++;
                        break;
                    } catch (IndexOutOfBoundsException e) {
                        // ignore
                    }
                }
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "test thread-safe set created from Collections.newSetFromMap")
    public void newSetFromSyncMap() {
        new ThreadRunner(2) {
            Set<Integer> s;

            @Override
            public void setUp() {
                s = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
            }

            @Override
            public void thread1() {
                sharedVar = 0;
                s.add(0);
            }

            @Override
            public void thread2() {
                while (!s.contains(0)) {
                    Thread.yield();
                }
                sharedVar = 1;
            }
        };
    }

    public static void main(String[] args) {
        JUCollectionTests tests = new JUCollectionTests();
        // positive tests
        if (args[0].equals("positive")) {
            tests.basicCollectionOps();
            tests.foreachLoop0();
            tests.foreachLoop1();
            tests.delegatedIterator();
            tests.basicMapOps();
            tests.collectionViewsOfMap();
//            tests.mapEntry();
            tests.differentSynchronizedViews();
            tests.iterateSyncCollectionWrong();
            tests.syncMapIterateCollectionViewWrong();
            tests.listIterator();
//            tests.conversionCtor();
        } else {
            // negative tests
            tests.readOnlyIteration();
            tests.synchronizedCollections();
            tests.syncMapIterateCollectionView();
            tests.syncListAddAndRemove();
            tests.newSetFromSyncMap();
        }
    }
}
