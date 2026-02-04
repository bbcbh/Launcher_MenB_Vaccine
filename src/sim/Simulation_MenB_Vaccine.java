package sim;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import relationship.ContactMap;

public class Simulation_MenB_Vaccine extends Simulation_MetaPopulation {

	public static void main(String[] args) throws IOException, InterruptedException {
		final String USAGE_INFO = String.format(
				"Usage: java %s - PROP_FILE_DIRECTORY "
						+ "<-export_skip_backup> <-printProgress> <-seedMap=SEED_MAP>\n",
				Simulation_MenB_Vaccine.class.getName());
		if (args.length < 1) {
			System.out.println(USAGE_INFO);
			System.exit(0);
		} else {
			Simulation_ClusterModelTransmission.launch(args, new Simulation_MenB_Vaccine());
		}

	}

	@Override
	public Abstract_Runnable_ClusterModel_Transmission generateDefaultRunnable(long cMap_seed, long sim_seed,
			Properties loadedProperties) {

		String popType = (String) loadedProperties
				.get(SimulationInterface.PROP_NAME[SimulationInterface.PROP_POP_TYPE]);

		if (preGenSeedFile != null) {
			loadedProperties.put(Runnable_MenB_Vaccine.PROP_SEED_FILE_PATH, preGenSeedFile.getAbsolutePath());
		}

		if (Runnable_MenB_Vaccine_MSM.PROP_TYPE_PATTERN.matcher(popType).matches()) {
			return new Runnable_MenB_Vaccine_MSM(cMap_seed, sim_seed, loadedProperties);
		} else if (Runnable_MenB_Vaccine_RMP.PROP_TYPE_PATTERN.matcher(popType).matches()) {
			return new Runnable_MenB_Vaccine_RMP(cMap_seed, sim_seed, loadedProperties);
		} else {
			return null;
		}
	}

	@Override
	protected void loadAllContactMap(ArrayList<File> preGenClusterMap,
			HashMap<Long, ArrayList<File>> cmap_file_collection, HashMap<Long, ContactMap> cMap_Map)
			throws FileNotFoundException, IOException, InterruptedException {

		// Single load only
		for (File element : preGenClusterMap) {
			System.out.printf("Loading (in series) on ContactMap located at %s.\n", element.getAbsolutePath());
			Matcher m = Pattern.compile(REGEX_ALL_CMAP).matcher(element.getName());
			m.matches();
			long cMap_seed = Long.parseLong(m.group(1));
			ContactMap cMap = extractedCMapfromFile(element);
			cMap_Map.put(cMap_seed, cMap);
			cmap_file_collection.put(cMap_seed, new ArrayList<File>(List.of(element)));

		}

	}

	@Override
	protected void zipSelectedOutputs(String file_name, String zip_file_name)
			throws IOException, FileNotFoundException {

		if (preGenSeedFile != null) {
			final Pattern pattern_include_file = Pattern
					.compile("(\\[.*\\]){0,1}" + file_name.replaceAll("%d", "(-{0,1}(?!0)\\\\d+)"));
			zipSelectedOutputs(preGenSeedFile.getParentFile(), zip_file_name, pattern_include_file, exportSkipBackup);

		} else {
			super.zipSelectedOutputs(file_name, zip_file_name);
		}
	}

	@Override
	protected void finalise_simulations() throws IOException, FileNotFoundException {		
		if (preGenSeedFile != null && !baseDir.equals(preGenSeedFile.getParentFile())) {
			// Zip extra files
			Pattern pattern_csv_extra = Pattern.compile("(?:\\[.*\\]){0,1}(.*)_(-{0,1}\\d+)_-{0,1}\\d+.csv");
			Pattern pattern_csv_cMap = Pattern
					.compile(FILENAME_FORMAT_ALL_CMAP.replaceAll("%d", "(-{0,1}(?!0)\\\\d+)"));

			FileFilter extra_filter = new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return !pattern_csv_cMap.matcher(pathname.getName()).matches()
							&& pattern_csv_extra.matcher(pathname.getName()).matches();
				}
			};

			File[] extra_csv = preGenSeedFile.getParentFile().listFiles(extra_filter);
			while (extra_csv != null && extra_csv.length > 0) {
				Matcher m = pattern_csv_extra.matcher(extra_csv[0].getName());
				m.matches();
				String filename_id = m.group(1);
				String baseContactSeed_str = m.group(2);
				zipSelectedOutputs(String.format("%s_%s_%%d.csv", filename_id, baseContactSeed_str),
						String.format("%s_%s.csv.7z", filename_id, baseContactSeed_str));
				extra_csv = preGenSeedFile.getParentFile().listFiles(extra_filter);
			}
		}
	}

}
