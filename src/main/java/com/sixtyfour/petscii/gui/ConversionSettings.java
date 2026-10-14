package com.sixtyfour.petscii.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sixtyfour.petscii.Algorithm;

/**
 * All options of a conversion, mirroring the command line switches of
 * Petsciiator.
 */
public class ConversionSettings {

	public boolean tedMode = false;

	public Algorithm algorithm = Algorithm.COLORFUL;

	public int colorMode = 1;

	/** null means auto detect */
	public Integer background = null;

	public int prescale = 1;

	public boolean lowerCase = false;

	public boolean noAlpha = false;

	/** In tenths, i.e. 10 is the neutral value */
	public int symbolBoost = 10;

	public List<Integer> excludes = new ArrayList<>();

	public int dither = 100;

	public Set<String> formats = new LinkedHashSet<>();

	public boolean isBbs() {
		return formats.contains("bbs");
	}

	public int getMaxBackground() {
		return tedMode ? 127 : 15;
	}

	/**
	 * Parses a comma separated list of PETSCII char codes.
	 *
	 * @param text the list
	 * @return the codes
	 * @throws IllegalArgumentException if the list contains something that isn't
	 *                                  a valid char code
	 */
	public static List<Integer> parseExcludes(String text) {
		List<Integer> res = new ArrayList<>();
		if (text == null) {
			return res;
		}
		for (String part : text.split(",")) {
			part = part.trim();
			if (part.isEmpty()) {
				continue;
			}
			int code;
			try {
				code = Integer.parseInt(part);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("'" + part + "' isn't a number");
			}
			if (code < 0 || code > 255) {
				throw new IllegalArgumentException(code + " isn't a valid char code (0-255)");
			}
			res.add(code);
		}
		return res;
	}
}
