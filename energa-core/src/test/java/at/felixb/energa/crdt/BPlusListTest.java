package at.felixb.energa.crdt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BPlusListTest {

    @Test
    void emptyList_behavesCorrectly() {
        BPlusList<String> list = new BPlusList<>(3);

        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
        assertEquals(List.of(), list.toList());

        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
    }

    @Test
    void add_sequentialAndGet_correctOrder() {
        BPlusList<Integer> list = new BPlusList<>(3);

        int n = 1000;
        for (int i = 0; i < n; i++) {
            list.add(i);
        }

        assertEquals(n, list.size());

        for (int i = 0; i < n; i++) {
            assertEquals(i, list.get(i));
        }

        // toList() sollte denselben Inhalt haben
        assertEquals(n, list.toList().size());
        assertEquals(List.of(0, 1, 2, 3, 4), list.toList().subList(0, 5));
    }

    @Test
    void addAll_appendsInOrder() {
        BPlusList<String> list = new BPlusList<>(3);

        list.add("X");
        list.addAll(List.of("A", "B", "C"));

        assertEquals(4, list.size());
        assertEquals(List.of("X", "A", "B", "C"), list.toList());
    }

    @Test
    void insert_beginning_middle_end() {
        BPlusList<String> list = new BPlusList<>(3);

        list.add("B");
        list.add("D");
        list.add("E");         // [B, D, E]

        // vorne einfügen
        list.add(0, "A");      // [A, B, D, E]
        assertEquals(List.of("A", "B", "D", "E"), list.toList());

        // in die Mitte einfügen
        list.add(2, "C");      // [A, B, C, D, E]
        assertEquals(List.of("A", "B", "C", "D", "E"), list.toList());

        // ans Ende einfügen (index == size)
        list.add(list.size(), "F");   // [A, B, C, D, E, F]
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), list.toList());

        assertEquals(6, list.size());
        assertEquals("A", list.get(0));
        assertEquals("C", list.get(2));
        assertEquals("F", list.get(5));
    }

    @Test
    void insert_randomPositions_matchesArrayListBehaviour() {
        BPlusList<Integer> bplus = new BPlusList<>(4);
        List<Integer> arrayList = new ArrayList<>();

        Random rnd = new Random(42);
        int operations = 2000;

        for (int i = 0; i < operations; i++) {
            int value = i;
            int size = arrayList.size();
            int index = size == 0 ? 0 : rnd.nextInt(size + 1); // 0..size

            bplus.add(index, value);
            arrayList.add(index, value);

            assertEquals(arrayList.size(), bplus.size());

            // spot-check einige zufällige Indizes
            if (!arrayList.isEmpty()) {
                int checkIndex = rnd.nextInt(arrayList.size());
                assertEquals(arrayList.get(checkIndex), bplus.get(checkIndex));
            }
        }

        // finaler kompletter Vergleich
        assertEquals(arrayList, bplus.toList());
    }

    @Test
    void set_overwritesValue_keepsOrder() {
        BPlusList<String> list = new BPlusList<>(3);
        list.addAll(List.of("A", "B", "C"));

        String old = list.set(1, "X");
        assertEquals("B", old);
        assertEquals(List.of("A", "X", "C"), list.toList());

        old = list.set(0, "Y");
        assertEquals("A", old);
        assertEquals(List.of("Y", "X", "C"), list.toList());
    }

    @Test
    void get_outOfBounds_throws() {
        BPlusList<Integer> list = new BPlusList<>(3);
        list.add(1);
        list.add(2);

        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(5));
    }
}
