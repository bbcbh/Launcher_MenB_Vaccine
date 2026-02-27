package sim;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;

import random.RandomGenerator;

public class Runnable_MenB_Vaccine_RMP extends Runnable_MenB_Vaccine {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_RMP");

	private static final int NUM_INF = 1; // NG
	private static final int NUM_SITE = 2; // 0 = Penile, 1=Vaginal
	private static final int NUM_ACT = 1; // Penile-vaginal sex only

	protected static final String SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP = "SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP";
	protected static final String SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP = "SIM_OUTPUT_KEY_TREATMENT_BY_LOC_GRP";

	protected int[][] cumul_treatment_by_loc_grp;

	public static final String FILE_REGION_SPREAD = "Pop_IREG_RA.csv";

	// K = Region_Id, V = {grp, region_spread}
	protected HashMap<Integer, HashMap<Integer, double[]>> map_regions_spread_by_locGrp = null;

	public static final int FIELD_SOUGHT_TEST_RATE_RMP_CITY_ADJ = FIELD_SOUGHT_TEST_RATE + 1;
	public static final int FIELD_SOUGHT_TEST_RATE_RMP_REGIONAL_ADJ = FIELD_SOUGHT_TEST_RATE_RMP_CITY_ADJ + 1;
	public static final int FIELD_SOUGHT_TEST_RATE_RMP_REMOTE_ADJ = FIELD_SOUGHT_TEST_RATE_RMP_REGIONAL_ADJ + 1;

	// RNG
	protected RandomGenerator rng_region;

	public Runnable_MenB_Vaccine_RMP(long cMap_seed, long sim_seed, Properties prop) {
		super(cMap_seed, sim_seed, prop, NUM_INF, NUM_SITE, NUM_ACT);
		rng_region = rng_vaccine;

		File file_region_info = new File(prop.getProperty("PROP_CONTACT_MAP_LOC"));
		file_region_info = new File(file_region_info, FILE_REGION_SPREAD);

		if (file_region_info.isFile()) {
			map_regions_spread_by_locGrp = new HashMap<>();
			try {
				String[] ent = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(file_region_info);
				for (int lineNum = 1; lineNum < ent.length; lineNum++) {
					String[] line_ent = ent[lineNum].split(",");

					Integer ireg = Integer.valueOf(line_ent[0]);
					Integer grp = Integer.valueOf(line_ent[1]);
					double[] prob_region = new double[line_ent.length - 2];

					double pre_sum = 0;
					for (int i = 0; i < prob_region.length; i++) {
						prob_region[i] = Double.parseDouble(line_ent[i + 2]) + pre_sum;
						pre_sum = prob_region[i];
					}
					for (int i = 0; i < prob_region.length; i++) {
						prob_region[i] = prob_region[i] / prob_region[prob_region.length - 1];
					}

					HashMap<Integer, double[]> region_map = map_regions_spread_by_locGrp.get(ireg);
					if (region_map == null) {
						region_map = new HashMap<>();
						map_regions_spread_by_locGrp.put(ireg, region_map);
					}

					region_map.put(grp, prob_region);
				}
			} catch (IOException ex) {
				ex.printStackTrace(System.err);

			}

		}

	}

	protected int getVaccineGrp(int pid) {
		return getPersonGrp(pid);
	}

	@Override
	protected double getSeekTestRate(Integer pid) {
		double seek_test_rate = super.getSeekTestRate(pid);
		if (map_regions_spread_by_locGrp != null) {
			int[] indiv_ent = indiv_map.get(pid);
			int g = indiv_ent[INDIV_MAP_CURRENT_GRP];
			double[][] sym_seek_rate_field = (double[][]) getRunnable_fields()[RUNNABLE_FIELD_TRANSMISSION_SOUGHT_TEST_PERIOD_BY_SYM];
			for (double[] ent : sym_seek_rate_field) {
				if (ent.length >= FIELD_SOUGHT_TEST_RATE_RMP_REMOTE_ADJ) {
					int gender_inc = (int) ent[FIELD_SOUGHT_TEST_PERIOD_BY_SYM_GENDER_INC];
					if ((gender_inc & 1 << g) != 0) {
						int home_loc = indiv_ent[INDIV_MAP_HOME_LOC];
						double[] region_spread = map_regions_spread_by_locGrp.get(home_loc).get(g);
						double pRegion =  rng_region.nextDouble();
						int regPt = Arrays.binarySearch(region_spread,pRegion);
						if (regPt < 0) {
							regPt = ~regPt;
						}
						seek_test_rate *= ent[FIELD_SOUGHT_TEST_RATE_RMP_CITY_ADJ + regPt];
						break;
					}
				}
			}
		}

		return seek_test_rate;
	}

	@Override
	protected void applyTreatment(int currentTime, int infId, int pid, int[][] inf_stage) {
		super.applyTreatment(currentTime, infId, pid, inf_stage);

		if (cumul_treatment_by_loc_grp == null) {
			cumul_treatment_by_loc_grp = new int[NUM_GRP][location_name.length];
		}
		int[] indiv_stat = indiv_map.get(pid);
		int grp = indiv_stat[INDIV_MAP_CURRENT_GRP];
		if (grp >= 0) {
			int location = indiv_map.get(pid)[INDIV_MAP_CURRENT_LOC];
			int locPt = map_location.get(Integer.toString(location));
			cumul_treatment_by_loc_grp[grp][locPt]++;
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	protected void postTimeStep(int currentTime) {
		super.postTimeStep(currentTime);
		// Others steps

		if (currentTime % nUM_TIME_STEPS_PER_SNAP == 0) {
			String filePrefix = this.getRunnableId() == null ? "" : String.format("%s ", this.getRunnableId());
			PrintStream out = print_progress == null ? System.out : print_progress;

			for (int inf = 0; inf < cumul_incidence_by_person.length; inf++) {
				int cumul_incid = 0;
				for (int g = 0; g < cumul_incidence_by_person[inf].length; g++) {
					cumul_incid += cumul_incidence_by_person[inf][g];
				}
				out.printf("%sT = %d, Cumul. incidence #%d = %d. Generated at %tc\n", filePrefix, currentTime, inf,
						cumul_incid, System.currentTimeMillis());
			}

			// Add grp-location stat
			HashMap<Integer, int[][]> countGrpLoc;
			// Prevalence
			countGrpLoc = ((HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP));
			if (countGrpLoc == null) {
				countGrpLoc = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP, countGrpLoc);
			}

			int[][] countEnt = new int[NUM_GRP][location_name.length];
			ArrayList<Integer> infected_id_arr = new ArrayList<>();

			for (Entry<String, ArrayList<Integer>> ent : map_currently_infectious.entrySet()) {
				for (Integer pid : ent.getValue()) {
					int index = Collections.binarySearch(infected_id_arr, pid);
					if (index < 0) {
						int[] indiv_stat = indiv_map.get(pid);
						int grp = indiv_stat[INDIV_MAP_CURRENT_GRP];
						if (grp >= 0) {
							int location = indiv_map.get(pid)[INDIV_MAP_CURRENT_LOC];
							int locPt = map_location.get(Integer.toString(location));
							countEnt[grp][locPt]++;
						}
						infected_id_arr.add(~index, pid);
					}
				}
			}
			countGrpLoc.put(currentTime, countEnt);

			// Cumulative treatment
			countGrpLoc = ((HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP));

			if (countGrpLoc == null) {
				countGrpLoc = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP, countGrpLoc);
			}

			int[][] export_cumul_treatment = new int[NUM_GRP][location_name.length];
			if (cumul_treatment_by_loc_grp != null) {
				for (int g = 0; g < export_cumul_treatment.length; g++) {
					export_cumul_treatment[g] = Arrays.copyOf(cumul_treatment_by_loc_grp[g], location_name.length);
				}
			}
			countGrpLoc.put(currentTime, export_cumul_treatment);

		}

	}

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		super.postSimulation();

		String key, fileName;
		HashMap<Integer, int[]> countMap;
		String filePrefix = this.getRunnableId() == null ? "" : this.getRunnableId();
		final int[] COL_SEL_INF_GENDER = null;

		File seedFileDir = this.getSim_prop().containsKey(PROP_SEED_FILE_PATH)
				? new File((String) this.getSim_prop().get(PROP_SEED_FILE_PATH)).getParentFile()
				: baseDir;

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Group_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE) != 0) {
			key = String.format(SIM_OUTPUT_KEY_CUMUL_TREATMENT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE);

			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_TREATMENT_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			HashMap<Integer, int[][]> countGrpLoc = (HashMap<Integer, int[][]>) sim_output
					.get(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP);
			fileName = String.format(filePrefix + "Treatment_by_GrpLoc_%d_%d.csv", cMAP_SEED, sIM_SEED);
			File file_grp_loc = new File(seedFileDir, fileName);
			printLocGrpCount(countGrpLoc, file_grp_loc);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE) != 0) {

//			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_GENDER_COUNT,
//					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
//			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
//			fileName = String.format(
//					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_PERSON,
//					cMAP_SEED, sIM_SEED);
//			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			HashMap<Integer, int[][]> countGrpLoc = (HashMap<Integer, int[][]>) sim_output
					.get(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP);
			fileName = String.format(filePrefix + "Infectious_by_GrpLoc_%d_%d.csv", cMAP_SEED, sIM_SEED);

			File file_grp_loc = new File(seedFileDir, fileName);
			printLocGrpCount(countGrpLoc, file_grp_loc);

		}

	}

	private void printLocGrpCount(HashMap<Integer, int[][]> countGrpLoc, File file_grp_loc) {
		try {
			PrintWriter pri_grp_loc = new PrintWriter(file_grp_loc);
			StringBuilder header = new StringBuilder();
			header.append("Time");
			for (int g = 0; g < NUM_GRP; g++) {
				for (int loc = 0; loc < location_name.length; loc++) {
					header.append(',');
					header.append(String.format("Grp_%d_Loc_%s", g, location_name[loc]));
				}
			}
			pri_grp_loc.println(header.toString());

			Integer[] time_arr = countGrpLoc.keySet().toArray(new Integer[0]);
			Arrays.sort(time_arr);

			for (int t : time_arr) {
				pri_grp_loc.print(t);
				int[][] ent = countGrpLoc.get(t);
				for (int g = 0; g < NUM_GRP; g++) {
					for (int loc = 0; loc < location_name.length; loc++) {
						pri_grp_loc.print(',');
						pri_grp_loc.print(ent[g][loc]);
					}
				}
				pri_grp_loc.println();
			}

			pri_grp_loc.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

}
