package sim;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import person.AbstractIndividualInterface;
import random.MersenneTwisterRandomGenerator;
import random.RandomGenerator;
import util.PropValUtils;

public abstract class Runnable_MenB_Vaccine extends Runnable_MetaPopulation_MultiTransmission {

	public static final String PROP_VACCINE_PROPROPTIES = "PROP_VACCINE_PROPROPTIES";
	public static final String PROP_VACCINE_ALLOCATIONS = "PROP_VACCINE_ALLOCATIONS";

	protected static final String SIM_OUTPUT_KEY_VACC_COVERAGE = "SIM_OUTPUT_KEY_VACC_COVERAGE";
	protected static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED = 0;
	protected static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES = SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED
			+ 1;
	protected static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS = SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES
			+ 1;
	protected static final int LENGTH_SIM_OUTPUT_INDEX_VACC_COVERAGE = SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS
			+ 1;

	// Vaccine

	// Usage: double[]
	// {Dose_0_Site_0_Eff, Dose_0_Site_1_Eff, ...
	// Dose_0_Site_0_Waning_Rate_Per_Year, Dose_0_Site_1_Waning_Rate_Per_Year, ....
	// Dose_1_Site_0, ...]
	protected double[] vaccine_properties;

	// Usage: double[][]
	// { GLOBAL_START,VACCINE_GRP_INC, PROB_DOSE_0_AT_TEST,
	// PROB_NEXT_DOSE_0, NEXT_DOSE_AT_0,
	// PROB_NEXT_DOSE_1, NEXT_DOSE_AT_1,...}
	// or
	// { GLOBAL_START,~GRP_INC, PROB_DOSE_0_AT_GLOBAL_TIME_OR ENTRY,
	// PROB_NEXT_DOSE_0, NEXT_DOSE_AT_0,
	// PROB_NEXT_DOSE_1, NEXT_DOSE_AT_1,...}

	protected double[][] vaccine_allocate_all;
	protected int vaccine_allocate_next_row = -1;

	public static final int VACCINE_ALLOCATE_GLOBAL_START = 0;
	public static final int VACCINE_ALLOCATE_GRP_INC = VACCINE_ALLOCATE_GLOBAL_START + 1;
	public static final int VACCINE_ALLOCATE_PROB = VACCINE_ALLOCATE_GRP_INC + 1;

	// Key = GRP_INC, Val = vaccine_allocate_all rows
	protected HashMap<Integer, double[]> current_vaccination_strategy_by_grp_inc = new HashMap<>();
	// Key = PID , Val = Dose_time
	protected HashMap<Integer, ArrayList<Integer>> vaccination_history = new HashMap<>();
	// Key = Time , Val = PIDS
	protected HashMap<Integer, ArrayList<Integer>> schedule_booster = new HashMap<>();

	protected HashMap<String, Integer> location_map = new HashMap<>();

	// RNG
	protected RandomGenerator rng_vaccine;

	public Runnable_MenB_Vaccine(long cMap_seed, long sim_seed, Properties prop, int num_inf, int num_site,
			int num_act) {
		super(cMap_seed, sim_seed, prop, num_inf, num_site, num_act);

		vaccine_properties = (double[]) PropValUtils.propStrToObject(
				prop.getProperty(PROP_VACCINE_PROPROPTIES, Arrays.toString(new double[0])), double[].class);

		vaccine_allocate_all = (double[][]) PropValUtils.propStrToObject(
				prop.getProperty(PROP_VACCINE_ALLOCATIONS, Arrays.toString(new double[0][0])), double[][].class);

		rng_vaccine = new MersenneTwisterRandomGenerator(sim_seed);

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
			// {Dose_0_Site_0_Eff, Dose_0_Site_1_Eff, ...
			// Dose_0_Site_0_Waning_Rate_Per_Year, Dose_0_Site_1_Waning_Rate_Per_Year, ....
			// Dose_1_Site_0_Eff ....}

			int dose_pt = (dose_time_hist.size() - 1) * (this.NUM_SITE * 2);

			// Use the stat from last dose
			while ((dose_pt + this.NUM_SITE * 2) > vaccine_properties.length) {
				dose_pt -= this.NUM_SITE * 2;
			}

			double rate_wane_per_year = vaccine_properties[dose_pt + this.NUM_SITE + tar_site];
			double vacc_eff = vaccine_properties[dose_pt + tar_site];
			int days_since_last_dose = currentTime - dose_time_hist.get(dose_time_hist.size() - 1);

			if (days_since_last_dose > 5 * AbstractIndividualInterface.ONE_YEAR_INT) {
				vacc_eff = 0;
			} else {
				vacc_eff *= Math
						.exp((rate_wane_per_year * days_since_last_dose) / AbstractIndividualInterface.ONE_YEAR_INT);
				// Check for infection history
				ArrayList<ArrayList<Integer>> inf_hist = infection_history.get(pid_inf_tar);
				if (inf_hist != null) {
					// Vaccine efficiency = 0 if already infected twice or more
					ArrayList<Integer> ng_hist = inf_hist.get(0);
					if (ng_hist != null) {
						if (ng_hist.size() > 3) {
							vacc_eff = 0;
						}
					}

				}

			}

			trans_prob *= (1 - vacc_eff);
		}

		return trans_prob;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void postTimeStep(int currentTime) {
		super.postTimeStep(currentTime);
		if (vaccine_allocate_next_row == -1) {
			if (vaccine_allocate_all.length > 1) {
				// Sort vaccine_allocate_all by time
				Arrays.sort(vaccine_allocate_all, new Comparator<double[]>() {
					@Override
					public int compare(double[] o1, double[] o2) {
						int res = 0;
						int pt = 0;
						while (res == 0 && pt < o1.length) {
							res = Double.compare(o1[pt], o2[pt]);
							pt++;
						}
						return res;
					}
				});
			}
			vaccine_allocate_next_row = 0;
		}

		// Preset for next turn
		if (currentTime != 0) {
			updateCurrentVaccinationStrategy(currentTime + 1);
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

	protected abstract int getVaccineGrp(int pid);

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
						vaccine_allocate_all[row][index] = point[i];

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

	protected void updateCurrentVaccinationStrategy(int updateTime) {

		while (vaccine_allocate_next_row < vaccine_allocate_all.length
				&& vaccine_allocate_all[vaccine_allocate_next_row][VACCINE_ALLOCATE_GLOBAL_START] < updateTime) {
			vaccine_allocate_next_row++;
		}

		for (int r = vaccine_allocate_next_row; r < vaccine_allocate_all.length; r++) {
			if (vaccine_allocate_all[r][VACCINE_ALLOCATE_GLOBAL_START] == updateTime) {
				int grpInc = (int) (vaccine_allocate_all[r][VACCINE_ALLOCATE_GRP_INC]);
				current_vaccination_strategy_by_grp_inc.put(grpInc, vaccine_allocate_all[r]);

				if (grpInc < 0) {

					// First vaccination at intro time
					double[] current_vaccine_allocation = current_vaccination_strategy_by_grp_inc.get(grpInc);
					double pDoseAtGrp = current_vaccine_allocation[VACCINE_ALLOCATE_PROB];

					int grpInc_age = ~grpInc;
					int grp_sel = 0;

					int vaccination_count = 0;
					int candidate_count = 0;

					while ((1 << grp_sel) <= grpInc_age) {
						if (((1 << grp_sel) & grpInc_age) != 0) {
							ArrayList<Integer> pids = current_pids_by_grp.get(grp_sel);
							for (Integer pid : pids) {
								if (rng_vaccine.nextDouble() < pDoseAtGrp) {
									vaccinate_person(pid, current_vaccine_allocation, updateTime);
									vaccination_count++;
								}
							}

							candidate_count += pids.size();
						}
						grp_sel++;
					}

					if (this.print_progress != null) {
						String filePrefix = this.getRunnableId() == null ? ""
								: String.format("%s ", this.getRunnableId());
						System.out.printf("%sT = %d: Mass vaccination of GrpInc=%d. # vaccinated = %d out of %d.\n",
								filePrefix, updateTime, grpInc_age, vaccination_count, candidate_count);
					}

				}

			}
		}

		if (schedule_booster.containsKey(updateTime)) {
			ArrayList<Integer> booster_pid = schedule_booster.remove(updateTime);
			for (Integer pid : booster_pid) {
				vaccination_history.get(pid).add(updateTime);
			}
		}
	}

	@Override
	protected void updateIndivMap(int time) {
		super.updateIndivMap(time);
		ArrayList<int[]> grpChange = schdule_grp_change.get(time);
		if (grpChange != null) {
			ArrayList<Integer> vaccine_grp_incl = new ArrayList<>();
			for (int grp_incl : current_vaccination_strategy_by_grp_inc.keySet()) {
				if (grp_incl < 0) {
					vaccine_grp_incl.add(grp_incl);
				}
			}
			if (!vaccine_grp_incl.isEmpty()) {
				for (int[] indiv_grp_change : grpChange) {
					int pid = indiv_grp_change[SCH_GRP_PID];
					int grp_change_to = indiv_grp_change[SCH_GRP_TO];
					for (int grp_incl : vaccine_grp_incl) {
						if (((1 << grp_change_to) & ~grp_incl) != 0) {
							double[] current_vaccine_allocation = current_vaccination_strategy_by_grp_inc.get(grp_incl);

							double pDoseAtGrp = current_vaccine_allocation[VACCINE_ALLOCATE_PROB];
							if (rng_vaccine.nextDouble() < pDoseAtGrp) {
								int pre_vaccine_time_offset = 0;
								// Backward vaccination start time if needed
								double pre_grp_vaccine_ratio = current_vaccine_allocation[VACCINE_ALLOCATE_GRP_INC]
										- (int) current_vaccine_allocation[VACCINE_ALLOCATE_GRP_INC];
								if (pre_grp_vaccine_ratio != 0) {
									int min_grp_age = grp_age_range[grp_change_to][0];
									int pre_vaccine_time = -(int) (pre_grp_vaccine_ratio * min_grp_age);
									pre_vaccine_time_offset = rng_vaccine.nextInt(pre_vaccine_time);
								}

								vaccinate_person(pid, current_vaccine_allocation, time - pre_vaccine_time_offset);
							}

						}
					}

				}
			}
		}

	}

	@Override
	public void testPerson(int currentTime, int pid_t, int infIncl, int siteIncl, int[][] cumul_treatment_by_person) {
		super.testPerson(currentTime, pid_t, infIncl, siteIncl, cumul_treatment_by_person);
		int pid = Math.abs(pid_t);

		if (!vaccination_history.containsKey(pid)) {
			int grp = getVaccineGrp(pid);
			for (Integer grpInc : current_vaccination_strategy_by_grp_inc.keySet()) {
				if (grpInc.intValue() > 0 && (grpInc.intValue() & 1 << grp) != 0) {
					double[] current_vaccine_allocation = current_vaccination_strategy_by_grp_inc.get(grpInc);

					// GLOBAL_START,RISK_GRP_INC,
					// PROB_DOSE_0_AT_TEST,PROB_NEXT_DOSE_0,NEXT_DOSE_AT_0,PROB_NEXT_DOSE_1,
					// NEXT_DOSE_AT_1,,..

					double pDoseAtTest = current_vaccine_allocation[VACCINE_ALLOCATE_PROB];
					if (rng_vaccine.nextDouble() < pDoseAtTest) {
						vaccinate_person(pid, current_vaccine_allocation, currentTime);
					}
				}
			}
		}
	}

	protected void vaccinate_person(int pid, double[] current_vaccine_allocation, int vaccineTime) {
		ArrayList<Integer> vac_hist = vaccination_history.get(pid);
		if (vac_hist == null) {
			vac_hist = new ArrayList<>();
			vac_hist.add(vaccineTime);
			vaccination_history.put(pid, vac_hist);
		}
		// Check for booster
		boolean boosterEnd = false;

		for (int booster_prob_index = VACCINE_ALLOCATE_PROB + 1; booster_prob_index < current_vaccine_allocation.length
				&& !boosterEnd; booster_prob_index += 2) {

			boosterEnd = !(rng_vaccine.nextDouble() < current_vaccine_allocation[booster_prob_index]);
			if (!boosterEnd) {
				double mean_booster_schedule = current_vaccine_allocation[booster_prob_index + 1];
				int booster_time = vaccineTime + (int) Math.round(mean_booster_schedule);

				ArrayList<Integer> booster_pid = schedule_booster.get(booster_time);
				if (booster_pid == null) {
					booster_pid = new ArrayList<>();
					schedule_booster.put(booster_time, booster_pid);
				}
				booster_pid.add(pid);

			}

		}
	}

	@Override
	public void printCountMap(HashMap<Integer, int[]> countMap, String fileName, String headerFormat, int[] dimension,
			int[] col_to_print) {

		String outputFileName = fileName;

		if (this.getSim_prop().containsKey(PROP_SEED_FILE_PATH)) {
			File seedFileDir = new File((String) this.getSim_prop().get(PROP_SEED_FILE_PATH)).getParentFile();
			outputFileName = String.format("%s%s%s", seedFileDir.getName(), File.separator, fileName);
		}

		super.printCountMap(countMap, outputFileName, headerFormat, dimension, col_to_print);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		super.postSimulation();
		HashMap<Integer, int[]> countMap;

		String fileName;
		PrintWriter pWri;
		String filePrefix = this.getRunnableId() == null ? "" : this.getRunnableId();
		File outputBase = baseDir;

		if (this.getSim_prop().containsKey(PROP_SEED_FILE_PATH)) {
			outputBase = new File((String) this.getSim_prop().get(PROP_SEED_FILE_PATH)).getParentFile();
		}

		if (sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE) != null) {
			countMap = (HashMap<Integer, int[]>) sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE);
			fileName = String.format(filePrefix + "Vaccine_Coverage_%d_%d.csv", cMAP_SEED, sIM_SEED);

			try {
				pWri = new PrintWriter(new java.io.File(outputBase, fileName));
				Integer[] timeArr = countMap.keySet().toArray(new Integer[0]);
				Arrays.sort(timeArr);

				pWri.println("Time,Ever_Vaccinated,Multi_Doses,Last_Dose_5YPlus");
				for (Integer time : timeArr) {
					int[] ent = countMap.get(time);
					pWri.printf("%d,%d,%d,%d\n", time, ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED],
							ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES],
							ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS]);
				}

				pWri.close();
			} catch (IOException ex) {
				ex.printStackTrace(System.err);
			}

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_TRACK_INFECTION_HISTORY) > 0) {

			Integer[] pids = infection_history.keySet().toArray(new Integer[infection_history.size()]);
			Arrays.sort(pids);
			try {
				pWri = new PrintWriter(new File(outputBase,
						String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_INFECTION_HISTORY,
								cMAP_SEED, sIM_SEED)));
				pWri.println("ID,INF_ID,History");
				for (Integer pid : pids) {
					ArrayList<ArrayList<Integer>> hist = infection_history.get(pid);
					for (int infId = 0; infId < hist.size(); infId++) {
						pWri.print(pid.toString());
						pWri.print(',');
						pWri.print(infId);
						for (Integer timeEnt : hist.get(infId)) {
							pWri.print(',');
							pWri.print(timeEnt);
						}
						pWri.println();
					}
				}
				pWri.close();

			} catch (IOException e) {
				e.printStackTrace(System.err);
			}

			// Print vaccination history
			Integer[] pids_vacc = vaccination_history.keySet().toArray(new Integer[0]);
			Arrays.sort(pids_vacc);
			if (pids_vacc.length > 0) {
				try {
					pWri = new PrintWriter(new File(outputBase,
							String.format(filePrefix + "Vaccination_Hist_%d_%d.csv", cMAP_SEED, sIM_SEED)));
					pWri.println("ID,Vaccine_History");
					for (Integer pid : pids_vacc) {
						ArrayList<Integer> hist = vaccination_history.get(pid);
						pWri.print(pid.toString());
						for (Integer timeEnt : hist) {
							pWri.print(',');
							pWri.print(timeEnt);
						}
						pWri.println();
					}
					pWri.close();

				} catch (IOException e) {
					e.printStackTrace(System.err);
				}

			}

		}

	}

}
