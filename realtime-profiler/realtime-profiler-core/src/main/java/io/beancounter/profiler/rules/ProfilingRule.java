package io.beancounter.profiler.rules;

import io.beancounter.commons.linking.LinkingEngine;
import io.beancounter.commons.model.Interest;
import io.beancounter.commons.nlp.NLPEngine;

import java.util.Collection;
import java.util.Properties;

/**
 * put class description here
 *
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public interface ProfilingRule {

    public NLPEngine getNLPEngine();

    public LinkingEngine getLinkingEngine();

    public void run(Properties properties) throws ProfilingRuleException;

    public Collection<Interest> getResult() throws ProfilingRuleException;

}
