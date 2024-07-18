import numpy as np
from scipy.stats import wilcoxon
from tqdm import tqdm  # for progress bar
from statsmodels.stats.multitest import multipletests


# Function to simulate data with a given effect size for multiple datasets and patchers
def simulate_data(
    num_datasets, num_patchers, n, effect_size, deviation, distribution="normal"
):
    # Simulate metrics for your patcher (patcher 0) and other patchers (1 to num_patchers-1)
    data_your_patcher = np.random.normal(loc=0.5, scale=0.02, size=(num_datasets, 1, n))
    data_other_patchers = data_your_patcher + np.random.normal(
        loc=effect_size, scale=0.02, size=(num_datasets, num_patchers - 1, n)
    )
    return data_your_patcher, data_other_patchers


# Function to perform power analysis via simulation
def power_analysis_simulation(
    num_simulations,
    num_datasets,
    num_patchers,
    n,
    effect_size,
    deviation,
    alpha=0.05,
    distribution="normal",
):
    power_counts = np.zeros(
        (num_datasets, num_patchers - 1)
    )  # Power counts for each dataset and comparison
    for _ in tqdm(range(num_simulations)):
        # Simulate data for this iteration
        data_your_patcher, data_other_patchers = simulate_data(
            num_datasets, num_patchers, n, effect_size, deviation, distribution
        )

        # Perform comparisons for each dataset
        for dataset_idx in range(num_datasets):
            for patcher_idx in range(num_patchers - 1):
                yours = data_your_patcher[dataset_idx, 0]
                others = data_other_patchers[dataset_idx, patcher_idx]
                # Compare your patcher against each of the other patchers
                stat, p_value = wilcoxon(
                    yours,
                    others,
                )

                # Check if the result is significant
                if p_value < alpha:
                    power_counts[dataset_idx, patcher_idx] += 1
    # Estimate power as the proportion of simulations where the effect was detected
    estimated_power = power_counts / num_simulations
    return estimated_power


def main():
    # Parameters for the power analysis
    num_simulations = 1000  # Number of simulations to perform
    num_datasets = 10  # Number of datasets
    num_patchers = 5  # Total number of patchers, including your patcher
    n = 20  # Sample size (number of repetitions per dataset)
    effect_size = (
        0.02  # Assumed effect size (difference between your patcher and others)
    )
    alpha = 0.05  # Significance level
    deviation = 0.01

    # Adjusted significance level after multiple testing correction
    corrected_alpha = multipletests(
        [alpha] * num_datasets * (num_patchers - 1), alpha=alpha, method="fdr_bh"
    )[3]
    # Perform the power analysis using the corrected significance level
    estimated_power = power_analysis_simulation(
        num_simulations,
        num_datasets,
        num_patchers,
        n,
        effect_size,
        deviation,
        corrected_alpha,
        distribution="normal",
    )
    # Print the estimated power for each dataset and comparison
    for dataset_idx in range(num_datasets):
        for patcher_idx in range(num_patchers - 1):
            print(
                f"Estimated power for dataset {dataset_idx+1}, comparing your patcher with patcher {patcher_idx+1}: {estimated_power[dataset_idx, patcher_idx]:.3f}"
            )
    # You can average the power across all datasets and comparisons if you want a single summary statistic
    average_power = np.mean(estimated_power)
    print(
        f"Average estimated power across all datasets and comparisons: {average_power:.3f}"
    )
