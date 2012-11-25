package io.beancounter.storm.analyses;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.beancounter.commons.model.activity.Activity;
import io.beancounter.commons.model.activity.Tweet;
import org.codehaus.jackson.map.ObjectMapper;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.Map;

/**
 * Splits a Tweet's text up into words according to the specified Locale and
 * outputs a tuple for each word:
 *      [ word:string ]
 *
 * @author Alex Cowell
 */
public class WordSplitter extends BaseRichBolt {

    private final Locale locale;

    private OutputCollector collector;

    public WordSplitter(Locale locale) {
        this.locale = locale;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String tweetJson = tuple.getString(0);
        collector.ack(tuple);

        Tweet tweet;
        try {
            tweet = (Tweet) new ObjectMapper().readValue(tweetJson, Activity.class).getObject();
        } catch (Exception ex) {
            return;
        }

        String text = tweet.getText().toLowerCase(locale);
        BreakIterator boundary = BreakIterator.getWordInstance(locale);
        boundary.setText(text);

        int start = boundary.first();
        for (int end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
            String word = text.substring(start, end);
            if (Character.isLetterOrDigit(word.codePointAt(0))) {
                collector.emit(new Values(word));
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("word"));
    }
}
