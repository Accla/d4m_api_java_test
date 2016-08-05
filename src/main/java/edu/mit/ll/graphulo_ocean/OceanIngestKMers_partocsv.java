package edu.mit.ll.graphulo_ocean;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;
import com.google.common.base.Preconditions;
import com.google.common.io.PatternFilenameFilter;
import edu.mit.ll.graphulo_ocean.parfile.ParallelFileMapper;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Ex: java -cp "/home/dhutchis/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers_partocsv -inputDir "/home/dhutchis/gits/istc_oceanography/parse_fastq" -K 11
 * Ex: java -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers_partocsv -inputDir "/home/gridsan/dhutchison/gits/istc_oceanography/parse_fastq" -K 11
 * Ex: java -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers_partocsv -inputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed" -K 11 -numthreads 8 -lockDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_11_cnt_claim" -outputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_11_cnt"
 * Ex: java -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers_partocsv -inputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_non_overlapped" -K 11 -numthreads 8 -lockDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_non_overlapped_11_cnt_claim" -outputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_non_overlapped_11_cnt"
 * Ex: java -Xms4g -Xmx20g -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers_partocsv -inputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed" -K 13 -numthreads 7 -lockDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_13_cnt_claim" -outputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_13_cnt"
 * Ex: java -Xms4g -Xmx20g -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers_partocsv -inputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_non_overlapped" -K 13 -numthreads 7 -lockDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_non_overlapped_13_cnt_claim" -outputDir "/home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed_non_overlapped_13_cnt"
 */
public class OceanIngestKMers_partocsv {
  private static final Logger log = LogManager.getLogger(OceanIngestKMers_partocsv.class);

  public static void main(String[] args) {
    executeNew(args);
  }

  public static int executeNew(String[] args) { return new OceanIngestKMers_partocsv().execute(args); }

  private static class Opts extends Help {
//    @Parameter(names = {"-listOfSamplesFile"}, required = true)
//    public String listOfSamplesFile;

//    @Parameter(names = {"-everyXLines"})
//    public int everyXLines = 1;

//    @Parameter(names = {"-startOffset"})
//    public int startOffset = 0;

    @Parameter(names = {"-K"}, required = true)
    public int K;

    @Parameter(names = {"-inputDir"}, required = true, converter = FileConverter.class)
    public File inputDir;

    @Parameter(names = {"-lockDir"}, converter = FileConverter.class)
    public File lockDir;

    @Parameter(names = {"-outputDir"}, converter = FileConverter.class)
    public File outputDir;

    @Parameter(names = {"-numthreads"})
    public int numthreads = 1;

    @Override
    public String toString() {
      return "Opts{" +
          "K=" + K +
          ", inputDir=" + inputDir +
          ", lockDir=" + lockDir +
          ", outputDir=" + outputDir +
          ", numthreads=" + numthreads +
          '}';
    }
  }

  /** @return Number of files processed */
  public int execute(final String[] args) {
    final Opts opts = new Opts();
    opts.parseArgs(OceanIngestKMers_partocsv.class.getName(), args);
    log.info(OceanIngestKMers_partocsv.class.getName() + " " + opts);
    Preconditions.checkArgument(opts.inputDir.exists() && opts.inputDir.isDirectory(), "input dir does not exist");
    if (opts.lockDir == null)
      opts.lockDir = new File(opts.inputDir, "lockDir_cnt");
    if (opts.outputDir == null)
      opts.outputDir = new File(opts.inputDir, "outputDir_cnt");
    if (!opts.outputDir.exists()) {
      try {
        Thread.sleep((long)(Math.random() * 1000));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (!opts.outputDir.exists())
        //noinspection ResultOfMethodCallIgnored
        opts.outputDir.mkdirs();
    }
    final GenomicEncoder G = new GenomicEncoder(opts.K);

    final CSVIngesterKmer.KmerAction kmerAction = new CSVIngesterKmer.KmerAction() {
      @Override
      public void run(String sampleid, SortedMap<CSVIngesterKmer.ArrayHolder, Integer> map) {
        File outFile = new File(opts.outputDir, sampleid+"_"+opts.K+"_cnt.csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
          for (Map.Entry<CSVIngesterKmer.ArrayHolder, Integer> entry : map.entrySet()) {
            writer.write(G.decode(entry.getKey().b));
            writer.write(','+entry.getValue().toString()+'\n');
          }
        } catch (IOException e) {
          log.error("error writing to file "+outFile, e);
        }
      }
    };

    final ParallelFileMapper.FileAction fileAction = new ParallelFileMapper.FileAction() {
      @Override
      public void run(File f) {
        CSVIngesterKmer ingester = new CSVIngesterKmer(opts.K, kmerAction);
        try {
          ingester.ingestFile(f);
        } catch (IOException e) {
          log.error("error reading file "+f, e);
        }
      }
    };

    @SuppressWarnings("ConstantConditions")
    List<File> inputFiles = Arrays.asList(opts.inputDir.listFiles(new PatternFilenameFilter(".*\\.csv$")));

    Thread[] threads = new Thread[opts.numthreads];
    for (int i = 0; i < threads.length; i++)
      threads[i] = new Thread(new ParallelFileMapper(inputFiles, opts.lockDir, fileAction), "t" + i);
    for (Thread t : threads)
      t.start();
    for (Thread t : threads)
      try {
        t.join();
      } catch (InterruptedException e) {
        log.warn("while waiting for thread "+t, e);
      }

    return inputFiles.size();
  }

}