package co.nclk.batik;

import com.tinkerpop.blueprints.oupls.sail.GraphSail;
import com.thinkaurelius.titan.tinkerpop.rexster.TitanGraphConfiguration;
import org.apache.commons.configuration.Configuration;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.rexster.config.GraphConfigurationException;
import com.tinkerpop.rexster.config.GraphConfiguration;
import com.tinkerpop.rexster.config.GraphConfigurationContext;
import com.thinkaurelius.titan.core.TitanFactory;
import com.tinkerpop.blueprints.impls.sail.SailGraph;


public class BatikGraphSail extends TitanGraphConfiguration {

	@Override
	public Graph configureGraphInstance(final GraphConfigurationContext context) throws GraphConfigurationException {
//		return TitanFactory.open(this.convertConfiguration(context));
		return new SailGraph(
			new GraphSail(
				TitanFactory.open(this.convertConfiguration(context))
			)
		);
	}
}
