# Fever Lucene Baseline

This repository is a Lucene-based baseline for the evidence retrieval portion of Fever.
It is paired with [github.com/entilzha/fever](https://github.com/entilzha/fever) which contains scripts for converting the original data format to that needed by this code.

You can output predictions by running

```bash
gradle uberJar
java -Xmx32g -cp ~/Downloads/lucene-core-8.6.0.jar:~/Downloads/lucene-queryparser-8.6.0.jar:build/libs/fever-lucene-uber.jar ai.pedro.fever.AppKt ~/code/fever/data/wikipedia.kotlin.jsonl ~/code/fever/data/train.kotlin.jsonl ~/code/fever/data/lucene_preds_train.json
```

where the input files come from:

* JARs: download from maven, I had to explicitly put these in the classpath despite creating a FAT JAR due to an incompatible version installed on my system
* `kotlin.jsonl` files: Use the scripts in the mentioned repository
* You will also need to install the Kotlin compiler
