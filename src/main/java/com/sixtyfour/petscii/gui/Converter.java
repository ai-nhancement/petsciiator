package com.sixtyfour.petscii.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sixtyfour.petscii.Bitmap;
import com.sixtyfour.petscii.ColorMap;
import com.sixtyfour.petscii.ColorReducer;
import com.sixtyfour.petscii.ConvertedData;
import com.sixtyfour.petscii.HiEddiConverter;
import com.sixtyfour.petscii.HiresConverter;
import com.sixtyfour.petscii.KoalaConverter;
import com.sixtyfour.petscii.MulticolorConverter;
import com.sixtyfour.petscii.Petscii;
import com.sixtyfour.petscii.Saver;
import com.sixtyfour.petscii.TargetDimensions;
import com.sixtyfour.petscii.TedColors;
import com.sixtyfour.petscii.Vic2Colors;

/**
 * Runs the conversions for the GUI. Does the same as Petsciiator's convert()
 * but for a single file and with the results kept in memory for previewing.
 */
public class Converter {

	public static class PetsciiResult {
		public final Bitmap bitmap;
		public final ConvertedData data;

		PetsciiResult(Bitmap bitmap, ConvertedData data) {
			this.bitmap = bitmap;
			this.data = data;
		}
	}

	public static PetsciiResult convertToPetscii(File pic, ConversionSettings settings) {
		ColorMap colors = settings.tedMode ? new TedColors() : new Vic2Colors();
		Bitmap bitmap = new Bitmap(pic.getPath(), Math.max(1, Math.min(4, settings.prescale)));
		if (settings.background != null) {
			bitmap.setBackgroundColor(Math.max(0, Math.min(settings.getMaxBackground(), settings.background)));
		}
		bitmap.preprocess(settings.algorithm, colors, settings.colorMode);

		Petscii petscii = new Petscii(settings.lowerCase);
		if (settings.noAlpha) {
			petscii.removeAlphanumericChars();
		}
		if (settings.isBbs()) {
			petscii.removeControlCodes();
		}
		petscii.setSymbolBoost((float) settings.symbolBoost / 10);
		for (Integer exclude : settings.excludes) {
			petscii.excludeCharCodes(exclude);
		}

		ConvertedData data = bitmap.convertToPetscii(8, false, petscii, settings.tedMode);
		return new PetsciiResult(bitmap, data);
	}

	/**
	 * Same steps as KoalaConverter.convert(), but without writing a file.
	 */
	public static BufferedImage createKoalaPreview(File pic, ConversionSettings settings) {
		ColorMap colors = new Vic2Colors();
		Bitmap image = new Bitmap(pic.getPath(), 0, new TargetDimensions(160, 200, false), 1, -1);
		reduce(image, colors, settings.dither);
		return new MulticolorConverter(colors).convert(image).getImage();
	}

	/**
	 * Same steps as HiEddiConverter.convert(), but without writing a file.
	 */
	public static BufferedImage createHiresPreview(File pic, ConversionSettings settings) {
		ColorMap colors = new Vic2Colors();
		Bitmap image = new Bitmap(pic.getPath(), 0, new TargetDimensions(320, 200, false, 1), 1, -1);
		image.setBackgroundColor(6);
		reduce(image, colors, settings.dither);
		return new HiresConverter(colors).convert(image).getImage();
	}

	/**
	 * Writes all formats selected in the settings into the target folder.
	 *
	 * @return the written files
	 */
	public static List<String> export(File pic, ConversionSettings settings, File folder) {
		List<String> written = new ArrayList<>();
		if (settings.formats.contains("image") || settings.formats.contains("basic") || settings.formats.contains("bbs")
				|| settings.formats.contains("bin")) {
			PetsciiResult res = convertToPetscii(pic, settings);
			if (settings.formats.contains("image")) {
				written.add(Saver.savePetsciiImage(pic, res.bitmap, folder));
			}
			if (settings.formats.contains("basic")) {
				written.add(Saver.savePetsciiBasicCode(pic, res.data, folder));
			}
			if (settings.formats.contains("bbs")) {
				written.add(Saver.savePetsciiBbs(pic, res.data, folder));
			}
			if (settings.formats.contains("bin")) {
				written.addAll(Arrays.asList(Saver.savePetsciiBin(pic, res.data, folder)));
			}
		}
		float dither = ((float) Math.max(0, Math.min(100, settings.dither))) / 100f;
		if (settings.formats.contains("koala")) {
			File target = Saver.createTempFileName(pic, folder, "koala.koa");
			KoalaConverter.convert(pic.getPath(), target.getPath(), new Vic2Colors(), 1, dither, false);
			written.add(target.getPath());
		}
		if (settings.formats.contains("hires")) {
			File target = Saver.createTempFileName(pic, folder, "hires.hed");
			HiEddiConverter.convert(pic.getPath(), target.getPath(), new Vic2Colors(), 1, dither, false);
			written.add(target.getPath());
		}
		written.removeIf(path -> path == null);
		return written;
	}

	private static void reduce(Bitmap image, ColorMap colors, int dither) {
		float strength = ((float) Math.max(0, Math.min(100, dither))) / 100f;
		ColorReducer reducer = new ColorReducer();
		if (strength > 0) {
			reducer.reduce(image, colors, strength);
		} else {
			reducer.reduce(image, colors, false);
		}
	}
}
