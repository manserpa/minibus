# Large scale designing of public transport networks

This repository was originally a clone from the original [MATSim Minibus Contrib](https://github.com/matsim-org/matsim-libs/tree/master/contribs/minibus),
which was developed by [Neumann, A (2014)](https://svn.vsp.tu-berlin.de/repos/public-svn/publications/vspwp/2011/11-15/trb_paratransit_submitted_12nov11.pdf).

It is extended for the specific use-case of reproducing and optimizing the formal public transport network of the city of Zurich. The results have been
published in Manser, P., Becker, H., Hörl, S., & Axhausen, K. W. (2020). [Designing a large-scale public transport network using agent-based microsimulation](https://www.sciencedirect.com/science/article/abs/pii/S0965856420305668). *Transportation Research Part A: Policy and Practice*, **137**, 1-15.

The paper and [Manser, P. (2017)](https://ethz.ch/content/dam/ethz/special-interest/baug/ivt/ivt-dam/publications/students/501-600/sa575.pdf) provide an overview of the algorithm and the extensions made in this repository compared to the original code.


## Software

The code is very specifically tailored to the Zurich-application.

It uses a [MATSim-10 snapshot as of November 2017](https://dl.bintray.com/matsim-eth/matsim/org/matsim/matsim/0.10.0-nov17/). Further modules are
necessary to run the code:
- Discrete mode choice model (see [Hörl, S., M. Balac and K.W. Axhausen (2019)](https://www.research-collection.ethz.ch/handle/20.500.11850/303667) for more information)
- The [IVT Baseline Scenario](https://github.com/matsim-eth/baseline_scenario/tree/minibus)
- The router and the deterministic public transport simulation developed by [SBB](https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions)

Persons who are interested in applying the software using another scenario are very welcome to ask the author of this repository.
