package me._furiouspotato_.bingo;

import java.util.Comparator;
import java.util.Map;

public class BingoPlayerComparator implements Comparator<Map.Entry<String, BingoPlayer>> {

	public int compare(Map.Entry<String, BingoPlayer> a, Map.Entry<String, BingoPlayer> b) {
		if (a.getValue().score > b.getValue().score) {
			return -1;
		}
		if (a.getValue().score > b.getValue().score) {
			return 1;
		}
		return 0;
	}
}
