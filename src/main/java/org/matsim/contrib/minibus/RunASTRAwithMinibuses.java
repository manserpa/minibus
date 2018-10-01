package org.matsim.contrib.minibus;

import ch.ethz.matsim.baseline_scenario.BaselineModule;
import ch.ethz.matsim.baseline_scenario.analysis.simulation.ModeShareListenerModule;
import ch.ethz.matsim.baseline_scenario.transit.BaselineTransitModule;
import ch.ethz.matsim.baseline_scenario.transit.routing.DefaultEnrichedTransitRoute;
import ch.ethz.matsim.baseline_scenario.transit.routing.DefaultEnrichedTransitRouteFactory;
import ch.ethz.matsim.baseline_scenario.zurich.ZurichModule;
import ch.ethz.matsim.projects.astra.av.ASTRAQSimModule;
import ch.ethz.matsim.projects.astra.config.ASTRAConfigGroup;
import ch.ethz.matsim.projects.astra.mode_choice.ASTRAModeChoiceModule;
import ch.ethz.matsim.projects.astra.run.CommandLineConfigurator;
import ch.ethz.matsim.projects.astra.run.scenario.*;
import ch.ethz.matsim.projects.astra.scoring.ASTRAScoringModule;
import ch.ethz.matsim.projects.astra.traffic.ASTRATrafficModule;
import ch.ethz.matsim.projects.astra.utils.RemoveUnselecedPlans;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.minibus.agentReRouting.PReRoutingStrategy;
import org.matsim.contrib.minibus.hook.PModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;

public class RunASTRAwithMinibuses {
    static public void main(String[] args) {
        CommandLineConfigurator cmd = new CommandLineConfigurator(args,
                Arrays.asList("flow-efficiency", "freeflow", "sav", "sav-share", "fleet-size", "no-modechoice", "prav",
                        "prav-share", "only-replace-car", "no-prav-at-border", "pt-only-keep", "idsc-dispatcher",
                        "wt-calculator", "dispatcher-replanning-interval", "dispatcher", "sav-seats",
                        "parallel-trip-assignment", "scoring:*"));
        String configPath = cmd.getArguments().get(0);

        // Set up configuration
        Config config = ConfigUtils.loadConfig(configPath, new PConfigGroup(), new ASTRAConfigGroup(), new SBBTransitConfigGroup());
        cmd.apply(config);

        // Set up scenario
        Scenario scenario = ScenarioUtils.createScenario(config);
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DefaultEnrichedTransitRoute.class,
                new DefaultEnrichedTransitRouteFactory());
        ScenarioUtils.loadScenario(scenario);

        // overwrite existing transportmodes in schedule
        Set<Id<Link>> transitLinks = new HashSet<>();
        for(TransitLine line: scenario.getTransitSchedule().getTransitLines().values())	{
            for(TransitRoute route: line.getRoutes().values())	{
                route.setTransportMode("detPt");
                Id<Link> startLinkId = Id.createLinkId("pt_" + route.getRoute().getStartLinkId().toString());
                Id<Link> endLinkId = Id.createLinkId("pt_" + route.getRoute().getEndLinkId().toString());
                List<Id<Link>> linkList = new ArrayList<>();
                for(Id<Link> linkId: route.getRoute().getLinkIds()) {
                    linkList.add(Id.createLinkId("pt_" + linkId.toString()));
                    transitLinks.add(linkId);
                }
                transitLinks.add(route.getRoute().getStartLinkId());
                transitLinks.add(route.getRoute().getEndLinkId());
                NetworkRoute networkRoute = RouteUtils.createLinkNetworkRouteImpl(startLinkId, linkList, endLinkId);
                route.setRoute(networkRoute);
            }
        }

        for(TransitStopFacility stop: scenario.getTransitSchedule().getFacilities().values())   {
            stop.setLinkId(Id.createLinkId("pt_" + stop.getLinkId().toString()));
        }

        Set<Link> transitNetwork = new HashSet<>();
        Set<Link> linksToRemove = new HashSet<>();
        for(Link link: scenario.getNetwork().getLinks().values())    {
            if(!link.getAllowedModes().contains(TransportMode.car))
                linksToRemove.add(link);
            if(!transitLinks.contains(link.getId()))
                continue;
            NetworkFactory factory = scenario.getNetwork().getFactory();
            Link l = factory.createLink(Id.createLinkId("pt_" + link.getId().toString()), link.getFromNode(), link.getToNode());
            l.setNumberOfLanes(10000);
            l.setAllowedModes(CollectionUtils.stringToSet(TransportMode.pt));
            l.setLength(link.getLength());
            l.setCapacity(100000);
            l.setFreespeed(link.getFreespeed());
            transitNetwork.add(l);
        }

        for(Link link: transitNetwork)  {
            scenario.getNetwork().addLink(link);
        }

        for(Link link: linksToRemove)   {
            scenario.getNetwork().removeLink(link.getId());
        }

        // Configure scenario
        new RemoveUnselecedPlans().run(scenario.getPopulation());
        new FreeflowConfigurator(cmd).apply(config);
        new ModeChoiceConfigurator(cmd).apply(config);
        new InitialDemandConfigurator(cmd).apply(config, scenario);
        new PrivateAVConfigurator(cmd).apply(config, scenario);
        new LocalPtConfigurator(cmd).run(scenario);

        VehicleTypeConfigurator adjustVehicleTypes = new VehicleTypeConfigurator(config, cmd);
        adjustVehicleTypes.apply(scenario);

        //SAVConfigurator savConfigurator = new SAVConfigurator(cmd);
        //savConfigurator.apply(config);

        // Set up controller
        Controler controller = new Controler(scenario);

        //controller.addOverridingModule(new DvrpTravelTimeModule());
        //controller.addOverridingModule(new AVModule());

        controller.addOverridingModule(new BaselineModule());
        controller.addOverridingModule(new BaselineTransitModule());
        controller.addOverridingModule(new ZurichModule());

        controller.addOverridingModule(new ASTRATrafficModule());
        controller.addOverridingModule(new ASTRAScoringModule(cmd));
        controller.addOverridingModule(new ASTRAModeChoiceModule());
        //controller.addOverridingModule(new ASTRAQSimModule());
        controller.addOverridingModule(new ModeShareListenerModule());

        controller.addOverridingModule(adjustVehicleTypes);

        //controller.addOverridingModule(new ASTRAAVModule());
        //controller.addOverridingModule(new AnalysisModule());
        //controller.addOverridingModule(new AVWaitingTimeCalculatorModule());
        //controller.addOverridingModule(new AVPriceCalculationModule());
        //controller.addOverridingModule(new PravModule());

        controller.addOverridingModule(new PModule());

        controller.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addPlanStrategyBinding("PReRoute").toProvider(PReRoutingStrategy.class);
            }
        });

        //controller.addOverridingModule(savConfigurator);

        /*
        Config pConfig = ConfigUtils.createConfig();
        pConfig.network().setInputFile(config.network().getInputFile());
        Scenario pScenario = ScenarioUtils.loadScenario(pConfig);
        Network pNetwork = pScenario.getNetwork();
        linksToRemove = new HashSet<>();
        for(Link link : pNetwork.getLinks().values())	{
            if(!link.getAllowedModes().contains(TransportMode.car) || link.getFreespeed() > 27.0)
                linksToRemove.add(link);
        }
        for(Link link: linksToRemove)
            pNetwork.removeLink(link.getId());
        ConfigUtils.addOrGetModule(config, PConfigGroup.class).setPNetwork(pNetwork);
        */

        // randomly generate a seed between 1500 and 2500
        Random randomGenerator = new Random();
        int randomInt = randomGenerator.nextInt(1500) + 1000;
        long randomSeed = (long) (randomInt);
        config.global().setRandomSeed(randomSeed);
        String outputdir = config.controler().getOutputDirectory();
        config.controler().setOutputDirectory(outputdir + "output_seed_" + randomSeed);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.failIfDirectoryExists);

        controller.run();
    }
}

