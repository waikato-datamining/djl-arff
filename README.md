# djl-arff
Reading ARFF datasets using [Deep Java Library (DJL)](https://djl.ai/).

Rather than explicitly defining all the features and labels manually, 
the dataset builder a number of methods that simplify specifying
which columns are to be used as labels (i.e., class attributes/output variables) 
and which as features (i.e., input variables). It is also possible to
specify columns to ignore.

Works with DJL version 0.21.0 and later.


## Usage

Below is an example of how to load the UCI datatset iris, using the last column 
as class attribute and only features that match `petal.*`:

```java
import nz.ac.waikato.cms.adams.djl.dataset.ArffDataset;
import java.nio.file.Path;

ArffDataset dataset = ArffDataset.builder()
            .optArffFile(Path.of("src/main/resources/iris.arff"))
            .setSampling(32, true)
            .classIsLast()
            .addMatchingFeatures("petal.*")
            .build();
```

Here is an overview of the available `ArffDataset.ArffBuilder` methods:

* `dateColumnsAsNumeric()` - treat `DATE` attributes as `NUMERIC` instead of ignoring them
* `stringColumnsAsNominal()` - treat `STRING` attributes as `NOMINAL` instead of ignoring them
* `classIndex(int...)` - sets the 0-based index/indices of the column(s) to use as class attribute(s)  
* `classIsFirst()` - uses the first column as class attribute
* `classIsLast()` - uses the last column as class attribute
* `addClassColumn(String...)` - adds the specified column(s) as class attribute(s)
* `addIgnoredColumn(String...)` - specifies column(s) to be ignored
* `ignoreMatchingColumns(String...)` - ignores columns that match the regexp(s)
* `addAllFeatures()` - adds all columns as features that are neither ignored nor class attributes
* `addMatchingFeatures(String...)` - adds all columns that match the regexp(s) that are neither ignored nor class attributes
* `optArffFile(Path)` - the file to the ARFF file to load
* `optArffUrl(String)` - the URL of the ARFF file to load

Either `optArffFile` or `optArffUrl` needs to be specified. 


## Examples

Some example classes for loading ARFF files:

* [Load airline dataset](src/main/java/nz/ac/waikato/cms/adams/djl/dataset/example/LoadAirline.java)
* [Load bodyfat dataset (adding columns automatically)](src/main/java/nz/ac/waikato/cms/adams/djl/dataset/example/LoadBodyfatAutomatic.java)
* [Load bodyfat dataset (explicitly adding columns)](src/main/java/nz/ac/waikato/cms/adams/djl/dataset/example/LoadBodyfatExplicit.java)
* [Load iris dataset](src/main/java/nz/ac/waikato/cms/adams/djl/dataset/example/LoadIris.java)
* [Load iris dataset (STRING class attribute)](src/main/java/nz/ac/waikato/cms/adams/djl/dataset/example/LoadIrisString.java)


## Maven

Add the following dependency to your `pom.xml`:

```xml
    <dependency>
      <groupId>nz.ac.waikato.cms.adams</groupId>
      <artifactId>djl-arff</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>
```
