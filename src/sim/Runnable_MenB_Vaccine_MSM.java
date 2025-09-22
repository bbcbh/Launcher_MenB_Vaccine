package sim;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import person.AbstractIndividualInterface;
import random.MersenneTwisterRandomGenerator;
import random.RandomGenerator;
import relationship.ContactMap;
import util.PropValUtils;

public class Runnable_MenB_Vaccine_MSM extends Runnable_ClusterModel_MultiTransmission {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_MSM");

	public static final String PROP_VACCINE_PROPROPTIES = "PROP_VACCINE_PROPROPTIES";
	public static final String PROP_VACCINE_ALLOCATIONS = "PROP_VACCINE_ALLOCATIONS";

	private static final String SIM_OUTPUT_KEY_VACC_COVERAGE = "SIM_OUTPUT_KEY_VACC_COVERAGE";
	private static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED = 0;
	private static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES = SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED
			+ 1;
	private static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS = SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES
			+ 1;
	private static final int LENGTH_SIM_OUTPUT_INDEX_VACC_COVERAGE = SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS
			+ 1;

	private static final int num_inf = 3; // TP, NG and CT
	private static final int num_site = 4;
	private static final int num_act = 5;

	// FILENAME_PREVALENCE_PERSON, FILENAME_CUMUL_INCIDENCE_PERSON
	private static final int[] COL_SEL_INF_GENDER = new int[] { 6 };
	// FILENAME_CUMUL_INCIDENCE_SITE
	private static final int[] COL_SEL_INF_GENDER_SITE = new int[] { 25, 26, 27 };
	// "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_SITE
	private static final int[] COL_SEL_INF_SITE = new int[] { 5, 6, 7 };

	// Usage: double[]{Dose_0_Site_0, Dose_0_Site_1, ...
	// Dose_0_Site_0_Waning_Rate_Per_Year, Dose_0_Site_1_Waning_Rate_Per_Year, ....
	// Dose_1_Site_0, ...]
	protected double[] vaccine_properties;
	protected double[] current_vaccine_allocation = null;

	// Key = GLOBAL_START
	// Val =
	// {RISK_GRP_INC,PROB_DOSE_0_AT_TEST,PROB_NEXT_DOSE_0,NEXT_DOSE_AT_0,PROB_NEXT_DOSE_1,
	// NEXT_DOSE_AT_1,...}
	protected double[][] vaccine_allocate_all_default;
	protected HashMap<Integer, double[]> vaccine_allocation_setting;

	// Key = PID , Val = Dose_time
	protected HashMap<Integer, ArrayList<Integer>> vaccination_history = new HashMap<>();

	// Key = Time , Val = PIDS
	protected HashMap<Integer, ArrayList<Integer>> schedule_booster = new HashMap<>();

	protected RandomGenerator rng_vaccine;

	public Runnable_MenB_Vaccine_MSM(long cMap_seed, long sim_seed, ContactMap base_cMap, Properties prop) {
		super(cMap_seed, sim_seed, base_cMap, prop, num_inf, num_site, num_act);

		vaccine_properties = (double[]) PropValUtils.propStrToObject(
				prop.getProperty(PROP_VACCINE_PROPROPTIES, Arrays.toString(new double[0])), double[].class);

		vaccine_allocation_setting = new HashMap<>();
		vaccine_allocate_all_default = (double[][]) PropValUtils.propStrToObject(
				prop.getProperty(PROP_VACCINE_ALLOCATIONS, Arrays.toString(new double[0][0])), double[][].class);

		for (double[] vac_all : vaccine_allocate_all_default) {
			vaccine_allocation_setting.put((int) vac_all[0], Arrays.copyOfRange(vac_all, 1, vac_all.length));
		}

		rng_vaccine = new MersenneTwisterRandomGenerator(sim_seed);
	}

	@Override
	public ArrayList<Integer> loadOptParameter(String[] parameter_settings, double[] point, int[][] seedInfectNum,
			boolean display_only) {

		ArrayList<String> common_parameter_name = new ArrayList<>();
		ArrayList<Double> common_parameter_value = new ArrayList<>();

		for (int i = 0; i < parameter_settings.length; i++) {

			if (parameter_settings[i].startsWith(PROP_VACCINE_ALLOCATIONS)) {
				Matcher m = Pattern.compile(PROP_VACCINE_ALLOCATIONS + "_(\\d+)_(\\d+)").matcher(parameter_settings[i]);
				boolean suc = m.matches();
				if (suc) {
					try {
						int row = Integer.parseInt(m.group(1));
						int index = Integer.parseInt(m.group(2));

						double[] def_entry = vaccine_allocate_all_default[row];

						if (index == 0) {
							double[] ent = vaccine_allocation_setting.remove((int) def_entry[0]);
							vaccine_allocation_setting.put((int) point[i], ent);
							vaccine_allocate_all_default[row][0] = (int) point[i];
						} else {
							vaccine_allocation_setting.get((int) def_entry[0])[index - 1] = point[i];
						}

					} catch (ArrayIndexOutOfBoundsException e) {
						suc = false;
					}
				}

				if (!suc) {
					System.err.printf("Warning: Parameter for %s type mismatch. Value ignored.\n",
							parameter_settings[i]);
				}

			} else {
				common_parameter_name.add(parameter_settings[i]);
				common_parameter_value.add(point[i]);
			}
		}

		Double[] common_parameter_val_obj = common_parameter_value.toArray(new Double[common_parameter_value.size()]);

		double[] common_parameter_val = new double[common_parameter_value.size()];
		for (int i = 0; i < common_parameter_val.length; i++) {
			common_parameter_val[i] = common_parameter_val_obj[i].doubleValue();
		}

		return super.loadOptParameter(common_parameter_name.toArray(new String[common_parameter_name.size()]),
				common_parameter_val, seedInfectNum, display_only);
	}

	@Override
	protected double getTransmissionProb(int currentTime, int inf_id, int pid_inf_src, int pid_inf_tar,
			int partnershiptDur, int actType, int src_site, int tar_site) {
		double trans_prob = super.getTransmissionProb(currentTime, inf_id, pid_inf_src, pid_inf_tar, partnershiptDur,
				actType, src_site, tar_site);

		// Protective efficiency
		if (vaccination_history.containsKey(pid_inf_tar)) {
			ArrayList<Integer> dose_time_hist = vaccination_history.get(pid_inf_tar);
			// vaccine_properties: double[]
			// {Dose_0_Site_0, Dose_0_Site_1, Dose_0_Site_0_Waning_Rate_Per_Year,
			// Dose_0_Site_1_Waning_Rate_Per_Year, ....}
			//
			int dose_pt = (dose_time_hist.size() - 1) * (num_site * 2);

			// Use the stat from last dose
			while (dose_pt > vaccine_properties.length) {
				dose_pt -= num_site * 2;
			}

			double rate_wane_per_year = vaccine_properties[dose_pt + num_site + tar_site];
			double vacc_eff = vaccine_properties[dose_pt + tar_site];

			vacc_eff *= Math.exp((rate_wane_per_year * (currentTime - dose_time_hist.get(dose_time_hist.size() - 1)))
					/ AbstractIndividualInterface.ONE_YEAR_INT);

			trans_prob *= (1 - vacc_eff);
		}

		return trans_prob;
	}

	@Override
	protected void testPerson(int currentTime, int pid_t, int infIncl, int siteIncl,
			int[][] cumul_treatment_by_person) {
		super.testPerson(currentTime, pid_t, infIncl, siteIncl, cumul_treatment_by_person);
		// Vaccination by testing

		int pid = Math.abs(pid_t);

		if (current_vaccine_allocation != null && !vaccination_history.containsKey(pid)) {
			// Val =
			// {RISK_GRP_INC,PROB_DOSE_0_AT_TEST,PROB_NEXT_DOSE_0,NEXT_DOSE_AT_0,PROB_NEXT_DOSE_1,
			// NEXT_DOSE_AT_1,...}

			int riskGrp = risk_cat_map.get(pid);
			if ((((int) current_vaccine_allocation[0]) & 1 << riskGrp) != 0) {
				double pDoseAtTest = current_vaccine_allocation[1];
				if (rng_vaccine.nextDouble() < pDoseAtTest) {
					// First dose
					ArrayList<Integer> vac_hist = new ArrayList<>();
					vac_hist.add(currentTime);
					vaccination_history.put(pid, vac_hist);

					// Check for booster
					boolean boosterEnd = false;

					for (int booster_prob_index = 2; booster_prob_index < current_vaccine_allocation.length
							&& !boosterEnd; booster_prob_index += 2) {

						boosterEnd = !(rng_vaccine.nextDouble() < current_vaccine_allocation[booster_prob_index]);
						if (!boosterEnd) {
							double mean_booster_schedule = current_vaccine_allocation[booster_prob_index + 1];
							int booster_time = currentTime + (int) Math.round(mean_booster_schedule);						

							ArrayList<Integer> booster_pid = schedule_booster.get(booster_time);
							if (booster_pid == null) {
								booster_pid = new ArrayList<>();
								schedule_booster.put(booster_time, booster_pid);
							}
							booster_pid.add(pid);

						}

					}

				}

			}

		}

	}

	@SuppressWarnings("unchecked")
	@Override
	protected void postTimeStep(int currentTime) {
		super.postTimeStep(currentTime);
		// Preset for next turn
		if (currentTime != 0) {
			int nextTime = currentTime + 1;
			if (vaccine_allocation_setting.containsKey(nextTime)) {
				double[] vac_alloc = vaccine_allocation_setting.get(nextTime);
				current_vaccine_allocation = Arrays.copyOf(vac_alloc, vac_alloc.length);
			}
			if (schedule_booster.containsKey(nextTime)) {
				ArrayList<Integer> booster_pid = schedule_booster.remove(nextTime);
				for (Integer pid : booster_pid) {
					vaccination_history.get(pid).add(nextTime);
				}
			}
		}

		if (currentTime % nUM_TIME_STEPS_PER_SNAP == 0) {
			HashMap<Integer, int[]> countMap;
			countMap = (HashMap<Integer, int[]>) sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE);
			if (countMap == null) {
				countMap = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_VACC_COVERAGE, countMap);
			}
			int[] dose_stat = new int[LENGTH_SIM_OUTPUT_INDEX_VACC_COVERAGE];
			dose_stat[SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED] = vaccination_history.size();
			for (Entry<Integer, ArrayList<Integer>> ent : vaccination_history.entrySet()) {
				if (ent.getValue().size() > 1) {
					dose_stat[SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES]++;
				}
				if ((currentTime - ent.getValue().get(ent.getValue().size() - 1)) > 5
						* AbstractIndividualInterface.ONE_YEAR_INT) {
					dose_stat[SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS]++;
				}
			}
			countMap.put(currentTime, dose_stat);
		}

	}

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		super.postSimulation();

		String key, fileName;
		HashMap<Integer, int[]> countMap;
		String filePrefix = this.getRunnableId() == null ? "" : this.getRunnableId();

		if (sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE) != null) {
			countMap = (HashMap<Integer, int[]>) sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE);
			fileName = String.format(filePrefix + "Vaccine_Coverage_%d_%d.csv", cMAP_SEED, sIM_SEED);

			try {
				PrintWriter pWri = new PrintWriter(new java.io.File(baseDir, fileName));
				Integer[] timeArr = countMap.keySet().toArray(new Integer[0]);
				Arrays.sort(timeArr);
				
				pWri.println("Time,Ever_Vaccinated,Multi_Doses,Last_Dose_5YPlus");
				for(Integer time : timeArr) {					
					int[] ent = countMap.get(time);
					pWri.printf("%d,%d,%d,%d\n", time,
							ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED],
							ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES], ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS]);
				}				
				
				pWri.close();
			} catch (IOException ex) {
				ex.printStackTrace(System.err);

			}

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE) != 0) {
			key = String.format(SIM_OUTPUT_KEY_CUMUL_TREATMENT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE);

			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_TREATMENT_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP },
					COL_SEL_INF_GENDER);

		}
		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP },
					COL_SEL_INF_GENDER);

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE_SITE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);

			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_SITE,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d_Site_%d", new int[] { NUM_INF, NUM_GRP, NUM_SITE },
					COL_SEL_INF_GENDER_SITE);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_GENDER_COUNT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(
					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP },
					COL_SEL_INF_GENDER);

			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_SITE_COUNT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(
					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_SITE,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Site_%d", new int[] { NUM_INF, NUM_SITE }, COL_SEL_INF_SITE);

		}

		if (print_progress != null && runnableId != null) {
			try {
				print_progress.printf("Post simulation file generation for Thread <%s> completed. Timestamp = %tc.\n",
						runnableId, System.currentTimeMillis());
			} catch (Exception ex) {
				System.err.printf("Post simulation file generation for Thread <%s> completed.\n", runnableId);
			}
		}

	}

}
