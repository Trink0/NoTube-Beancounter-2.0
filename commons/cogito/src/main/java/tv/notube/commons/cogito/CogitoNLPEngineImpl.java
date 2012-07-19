package tv.notube.commons.cogito;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tv.notube.commons.cogito.model.Entities;
import tv.notube.commons.cogito.model.Relevant;
import tv.notube.commons.cogito.model.Relevants;
import tv.notube.commons.cogito.model.Response;
import tv.notube.commons.cogito.parser.DigesterParser;
import tv.notube.commons.nlp.*;
import tv.notube.commons.redirects.RedirectException;
import tv.notube.commons.redirects.RedirectResolver;

import java.io.*;
import java.net.*;

/**
 * Cogito-based implementation of {@link NLPEngine}.
 *
 * @author Enrico Candino ( enrico.candino@gmail.com )
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public class CogitoNLPEngineImpl implements NLPEngine {

    private final static Logger LOGGER = LoggerFactory.getLogger(CogitoNLPEngineImpl.class);

    private final static String BASE_URI = "http://dati.rai.tv/";

    private final static String ENTITY = "entity/";

    private final static String RELEVANT ="category/";

    private final static String QUERY_PATTERN = "http://test.expertsystem.it/IPTC_ITA/EssexWS.asmx/ESSEXIndexdata";

    private String service;

    private DigesterParser parser;

    public CogitoNLPEngineImpl(String service) {
        this.service = service;
        parser = new DigesterParser();
    }

    @Override
    public NLPEngineResult enrich(String text) throws NLPEngineException {
        // just to check the QUERY_PATTERN (we can probably delete this)
        // TODO (med) move in the constructor
        try {
            new URL(QUERY_PATTERN);
        } catch (MalformedURLException e) {
            throw new NLPEngineException("url [" + QUERY_PATTERN + "] seems to be ill-formed", e);
        }
        PostMethod postMethod = new PostMethod(QUERY_PATTERN);
        postMethod.setParameter("text", text);
        HttpClient client = new HttpClient();
        int response;
        try {
            response = client.executeMethod(postMethod);
        } catch (IOException e) {
            final String errMsg = "Error while POST-ing to [" + QUERY_PATTERN + "]";
            LOGGER.error(errMsg, e);
            throw new NLPEngineException(errMsg, e);
        }
        if (response != HttpStatus.SC_OK) {
            final String errMsg = "[" + QUERY_PATTERN + "] replied with HTTP [" + response + "]";
            LOGGER.error(errMsg);
            throw new NLPEngineException(errMsg, null);
        }
        InputStream is;
        try {
            is = postMethod.getResponseBodyAsStream();
        } catch (IOException e) {
            final String errMsg = "Error while getting response body as stream from [" + QUERY_PATTERN + "]";
            LOGGER.error(errMsg, e);
            throw new NLPEngineException(errMsg, e);
        }
        Response cogitoResponse;
        try {
            cogitoResponse = parser.parse(is);
        } catch (Exception e){
            final String errMsg = "Error while parsing response body from [" + QUERY_PATTERN + "]";
            LOGGER.error(errMsg);
            throw new NLPEngineException(errMsg, e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                final String errMsg = "Error while closing stream response body from [" + QUERY_PATTERN + "]";
                LOGGER.error(errMsg);
                throw new NLPEngineException(errMsg, e);
            }
        }
        return toResult(cogitoResponse);
    }

    private NLPEngineResult toResult(Response response) throws NLPEngineException {
        NLPEngineResult result = new NLPEngineResult();
        for (Entities entities : response.getEntities()) {
            String type = entities.getType();
            for (tv.notube.commons.cogito.model.Entity e : entities.getEntities()) {
                String uri = BASE_URI + ENTITY + e.getSyncon();
                Entity entity = Entity.build(uri, e.getName());
                entity.setType(type);
                result.addEntity(entity);
            }
        }
        for (Relevants relevants : response.getRelevants()) {
            for (Relevant r : relevants.getRelevants()) {
                double score = r.getScore();
                String uri;
                try {
                    uri = BASE_URI + RELEVANT + URLEncoder.encode(r.getName(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("UTF-8 is not supported", e);
                }
                Category category = Category.build(uri, r.getName());
                category.setScore(score);
                result.addCategory(category);
            }
        }
        return result;
    }

    @Override
    public NLPEngineResult enrich(URL url) throws NLPEngineException {
        URL resolved;
        try {
            resolved = RedirectResolver.resolve(url);
        } catch (RedirectException e) {
            throw new NLPEngineException(
                    "Error resolving the redirect for [" + url + "]",
                    e
            );
        }
        String text;
        try {
            // TODO (mid) pass text to boilerplate instead of url
            text = ArticleExtractor.INSTANCE.getText(resolved);
        } catch (BoilerpipeProcessingException e) {
            final String errMsg = "Error while removing boiler plate from [" + url + "]";
            LOGGER.error(errMsg, e);
            throw new NLPEngineException(errMsg, e);
        }
        return enrich(text);
    }

}
