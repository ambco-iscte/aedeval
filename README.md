<div align="center">

<picture style="height:3rem">
  <source style="height:3rem" media="(prefers-color-scheme: dark)" srcset="resources/logo-iscte-white.png">
  <source style="height:3rem" media="(prefers-color-scheme: light)" srcset="resources/logo-iscte-black.png">
  <img style="height:3rem" alt="AEDEval" src="resources/logo-iscte-white.png">
</picture>
<br>

# AED Automated Student Evaluator

**Java Library for Mass Testing and Analysis of Java Source Code Files**

[Introduction](#-introduction) •
[How to Use](#-how-to-use) •
[How are Assignments Graded?](#-how-does-aedeval-grade-assignments) •
[Worked Example](#-worked-example)

</div>

<br>

## 🛈 Introduction

**AED**eval is a software testing library developed for an [Algorithms and Data Structures](https://fenix-mais.iscte-iul.pt/courses/aled-284502928669423) course at 
[ISCTE-IUL](https://www.iscte-iul.pt/) in Lisbon, Portugal.

The library provides infrastructure for defining test cases for Java source code and their execution
_en masse_ for a large number of Java assignment submissions.

The library was developed with efficiency, security, and reasonable robustness in mind:
- The usage of Java's multithreading functionalities allows for a large number of submissions to be quickly evaluated in parallel;
- Test cases can be based on output assertions, execution time measurement, source code analysis, or a combination of each type of assessment;
- [JavaParser](https://javaparser.org/) is used to "clean" students' source code to remove `main` methods and `System` calls to prevent potentially damaging calls to the host system;
- [JPlag](https://github.com/jplag/JPlag) is used to automatically detect instances of plagiarism between submissions.

<br>

## 👨‍💻 How to Use
> [!IMPORTANT]  
> **AED**eval was developed exclusively for internal use in the Algorithms and Data Structures course of the second semester
of the first year of the Computer Science and Engineering programme at ISCTE-IUL. As such, some elements
may be overly specific to our usage context (e.g., expected file names from Moodle).
> You may use **AED**eval for your own purposes, but keep in mind that **we cannot guarantee that it will work out-of-the-box
in contexts different from ours!** Feel free to adapt your own copy of the code as you need.

<details>
<summary><b>Creating Tester Classes</b></summary>

Tester classes are the backbone of **AED**eval, since they're where the tests for a submission are defined.

Tester classes can be defined by inheriting from `Tester`, while test cases within those classes can be defined by annotating methods with `@Test` and `@Required`.

```java
import evaluator.Tester;
import evaluator.Submission;
import evaluator.annotations.*;

public class MyTester extends Tester {

    public MyTester(Submission submission) {
        super(submission);
    }

    @Require("Source.java")
    @Test(description = "", weight = 100)
    public void test() throws Exception {
        // ...
    }
}
```

The `@Require` annotation takes an array of file names for each Java source code file required to run the test case.

The `@Test` annotation defines the test case's description, its weight, which counts towards the submission's grade,
and, optionally, a penalty, which gets subtracted from the submission's grade should the test case fail.

</details>

<br>

<details>
<summary><b>Creating Submissions</b></summary>

A `Submission` can be manually instantiated by supplying:
1. The corresponding folder;
2. The name and ID of the submission;
3. A list of the files names of each expected/required file.

Instantiation can be done using only the submission's directory and the list of required file names,
in which case the name and ID are the folder name and its lowercase variant, respectively.

Alternatively, `Submission`s can be instantiated using their folder and the desired tester class for evaluating
the submission, if it is known at the moment of instantiation. **AED**eval will dynamically analyse
the tester class to determine which file names are required for correct test execution.

Here's an example of manually instantiating a `Submission`.

```java
import java.io.File;
import evaluator.Submission;

File folder = new File("path/to/submission");
String name = "This is a Cool Submission";
String id = "coolsubmission";
String[] requiredFiles = new String[] { "Foo.java", "Bar.java" };

// Using everything
Submission submission1 = new Submission(folder, name, id, requiredFiles);

// Using only the folder and the required files
Submission submission2 = new Submission(folder, requiredFiles);

// Using the folder and the tester class
Submission submission3 = new Submission(folder, MyTester.class);
```

</details>

<br>

<details>
<summary><b>Evaluating One Submission</b></summary>

A submission can be evaluated by instantiating a tester for that submission and running all test cases.

```java
import evaluator.Tester;
import evaluator.annotations.Test;
import evaluator.messages.Result;

Tester tester = new MyTester(mySubmission);
tester.runAllTests();
Map<Test, List<Result>> results = tester.getResults();
```

The tester result returns a map where each test case is paired with a list of results/messages for that test case's execution.

</details>

<br>

<details>
<summary><b>Evaluating Many Submissions</b></summary>

**AED**eval offers the `FullEvaluator` class, which allows for the automated testing of several submissions
as long as they are contained in the same parent folder.

```java
String parentFolder = "path/to/folder/containing/all/submissions";
Report report = new FullEvaluator<>(parentFolder, "Title", MyTester.class).run();
```

A `Report` object is essentially a collection of entries specifying a submission and the results of
every test executed for that submission, along with a general plagiarism analysis report generated by
[JPlag](https://github.com/jplag/JPlag).

Reports can be formatted and saved as Excel workbooks using the `XLSXReportWriter.write` method.

</details>

<br>

## 💯 How Does AEDeval Grade Assignments?

**The idea is that students are graded on each test case based on _how much of what could be executed did so correctly_.**

Each _assignment_ can have several _test cases_.  Each _test case_ can make several _assertions_ regarding the behaviour
of the student's code. Normally, each _test_ case targets a single method in the student's code.

An assignment's grade is the sum of the score obtained in each test case. The score of each test case
depends on how many assertions were executed,  how many of those were successful, and on its weight.

The grade of an assignment ranges between 0.0 and the sum of all test cases' weights.

A test case is considered to _fail_ if none of its assertions were correct, or if any of the assertion failures dictate the
failure of the entire case.

- For example, if an object needs to be instantiated and manipulated to execute a test case, and an unexpected exception on the student's code prevents an instance from being created, then the test case fails immediately as the remaining assertions cannot be executed.

Failed test cases subtract their penalty (default 0.0) from the assignment's total grade.

- This is to support cases where, for example, a student must comply with basic requirements for their assignment to be graded.

The following is the algorithm for grading an assignment.

```java
public double grade() {
    double grade = 0.0;
    for (TestCase test : getTestCases()) {
        int correct = test.getCorrectAssertions();
        int total = test.getTotalAssertions();
        if (total > 0) {
            if (failed(test))
                grade = Math.max(0.0, grade - test.penalty());
            else grade += ((double) correct / total) * test.weight();
        }
    }
    return grade;
}
```

For example, consider the following scenario:
1. Assignment **A1** has 3 test cases - **T1**, **T2**, and **T3** - each worth 10 points and with a penalty of 2 points;
2. **T1** executes 10 assertions, 4 of which were incorrect;
3. **T2** executes 2 assertions, one of which was incorrect;
4. **T3**'s assertions are all incorrect.

The total grade would be calculated as such:
1. **T1** contributes 6/10 of its 10 point weight, which is 6 points;
2. **T2** contributes 1/2 of its 10 point weight, which is 5 points;
3. **T3** fails, and so subtracts 2 points (penalty) from the total grade;
4. Hence, the final grade of assignment **A1** is 6 + 5 - 2 = 9 points.

<br>

## 🧑‍🏫 Worked Example

### 1. Example Assignment

Let's imagine students are tasked with a coding assignment, which we'll call Assignment 1, with the following example 
guidelines:
> Create a class Date which represents dates in MM/DD/YYYY format. Implement the `before` and
> `daysBetween` functions which, respectively: (1) checks whether a date is chronologically before another; and (2)
> counts how many days there are between two given dates.
>  
**Example:** 
```java
Date d1 = new Date(1, 1, 2020);   // January 1st, 2020
Date d2 = new Date(10, 20, 2020); // October 20th, 2020
System.out.println(d1.before(d2));
System.out.println(d1.daysBetween(d2));
```
### 2. Defining Test Cases

The rationale for defining test cases might go something like this: we should test calling the functions between several dates to match variations of the three parameters (month, day, and year).

Firstly, since we're working with Java reflection, it might be useful to define a helper method to create instances of our Date class.
```java
private ObjectInstantiation tryCreateDate(int month, int day, int year) throws Exception {
    Class<?>[] parameterTypes = {int.class, int.class, int.class}; // Constructor parameter types
    Object[] initArgs = {month, day, year}; // Constructor parameter values
    return instantiate(getClass("Date.java"), parameterTypes, initArgs);
}
```
Note that this returns an `ObjectInstantiation`, not directly an `Object`. This is because object instantiations, i.e.
constructor calls, may in themselves be submitted to evaluation. We'll look at this just ahead.

Let's start with the `before` method. We can create three Date instances and get the class's `before` function.
```java
Object d1 = tryCreateDate(1, 1, 2020).getOrFail();
Object d2 = tryCreateDate(10, 20, 2020).getOrFail();
Object d3 = tryCreateDate(10, 20, 1999).getOrFail();

Class<?> type = d1.getClass();
Method before = findMethod(type, "before", type); // Date.before(Date)
```
Several things to consider here:
1. `getOrFail` is a function of `ObjectInstantiation` which immediately fails a test case if the constructor call fails, i.e. throws an exception or times out (e.g., from an infinite loop). The rationale here is clear: if the student's code can't even correctly instantiate a Date, then nothing can be tested, since there won't even be an object to call functions on!
2. `findMethod` takes three arguments: the method's declaring class, its name, and a sequence of parameter types. In this case, the call corresponds to finding `Date.before(Date)`, since `type` is `Date.class`. A function to calculate the mean of two integers might look like `findMethod(class, "mean", int.class, int.class)`.

> [!NOTE]
> If the student didn't implement the method with the exact expected name, `findMethod` will try to match to the method with the closest name (within a reasonable [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance) of the expected name). If one is found, the test case proceeds with a slight penalty. If not, the test case fails as it has no method to evaluate.

All that's left now is to call the `before` method on our dates, and check that the return value matches the expected functionality:
```java
invoke(before, d1, d2).assertEquals(true);  // assert d1.before(d2) == true
invoke(before, d3, d1).assertEquals(true);  // assert d3.before(d1) == true
invoke(before, d3, d2).assertEquals(true);  // assert d3.before(d2) == true
invoke(before, d3, d3).assertEquals(false); // assert d3.before(d3) == false
```
The `invoke` function takes a `Method`, then the calling object, and finally a sequence of arguments. A static method (no calling object) might look like `invoke(foo, null, arg1, arg2, ...).`

All together, the test case might look like this: 👇

```java
@Require({"Date.java"})
@Test(description = "before", weight = 5)
public void testBefore() throws Exception {
    Object d1 = tryCreateDate(1, 1, 2020).getOrFail();
    Object d2 = tryCreateDate(10, 20, 2020).getOrFail();
    Object d3 = tryCreateDate(10, 20, 1999).getOrFail();

    Class<?> type = d1.getClass();
    Method before = findMethod(type, "before", type); // Date.before(Date)

    invoke(before, d1, d2).assertEquals(true);  // assert d1.before(d2) == true
    invoke(before, d3, d1).assertEquals(true);  // assert d3.before(d1) == true
    invoke(before, d3, d2).assertEquals(true);  // assert d3.before(d2) == true
    invoke(before, d3, d3).assertEquals(false); // assert d3.before(d3) == false
}
```
What's at play:
1. The `@Require` annotation, which says which source files are required for this test case;
2. The `@Test` annotation, which defines a method as an assignment test case with a description and a weight;
3. The `throws Exception` is needed in case of assertion failures.

With this in mind, consider the following test case for the `daysBetween` function: 👇
```java
@Require({"Date.java"})
@Test(description = "daysBetween", weight = 5)
public void testDaysBetween() throws Exception {
    Object d1 = tryCreateDate(1, 1, 2020).getOrFail();
    Object d2 = tryCreateDate(10, 20, 2020).getOrFail();
    Object d3 = tryCreateDate(10, 20, 1999).getOrFail();

    Class<?> type = d1.getClass();
    Method daysBetween = findMethod(type, "daysBetween", type);

    invoke(daysBetween, d1, d2).assertEqualsAny(292, 293, 294);
    invoke(daysBetween, d1, d3).assertEqualsAny(7376, 7377, 7378, 7379, -7376, -7377, -7378, -7379);
    invoke(daysBetween, d2, d3).assertEqualsAny(7669, 7670, 7671, 7672, -7669, -7670, -7671, -7672);
}
```

Like in the previous test case, we create three Date objects and find the `daysBetween` method. 

Now, something like "how many days between" may be ambiguous - do the dates themselves count? Does only the beginning date? Only the end date? Neither?

To accommodate these cases (and avoid unnecessary student frustration), we can use the `assertEqualsAny` assertion to check
whether a method call produces any of the given values. For example, the call `d1.daysBetween(d2)` would be considered correct if it returned 292, 293, or 294.

This should give you a basic idea of how test cases are defined. You can find more types of assertions in the [Tester](src/main/java/evaluator/Tester.java) class.

### 3. Evaluating Student Submissions

Say we have a folder `submissions/submission1` containing all student submissions for Assignment 1. 
(Folder names don't matter.)
We want to evaluate all of these submissions under the following conditions:
- Evaluation is multithreaded using 20 threads (20 submissions being evaluated in parallel at any given time);
- Any group of 5 or more students with 100% code similarity will be flagged for plagiarism.

We can create an evaluator for these submissions like so: 👇
```java
FullEvaluator<TestSubmission1> evaluator =
    new FullEvaluator<>(
        "submissions/submission1",      // Folder containing student submissions.
        "Submission 1 :)",              // Description.
        TestSubmission1.class           // Tester class we defined above!
    );
```

All we need to do now is run the evaluator and get the report! This is as easy as: 👇
```java
Report report = evaluator.run(20); // Runs the evaluator with 20 threads.
```
The number of threads to use is somewhat arbitrary; 20 threads is just something that works well on 
my machine. You can try using more or less depending on your application. Too few threads and a lot of submissions to 
evaluate might make the evaluation take a while!

Finally, to save the report as a neat Excel table, you can do: 👇
```java
XLSXReportWriter.write(
    report,                 // Report to be written.
    "Submission1Report",    // File name without extension. 
    5                       // Groups of 5 or more students with 100% code similarity get flagged for plagiarism.
);
```

And that's all! The report will contain a table where each row represents a students' evaluation.
Here's an example (anonymised from a real student submission):

![Report Excel row example](resources/report%20example.png)

There will also be a second sheet containing some general statistics about the submission's evaluation. Here's an example (from a real assignment):

![Report Excel plots example](resources/report%20stats%20example.png)

<br>

## ✉️ Contact

This project is currently owned, developed, and maintained by [Afonso Caniço](https://ciencia.iscte-iul.pt/authors/afonso-canico/cv). 
Feel free to reach out if you have any questions! Also, pull requests are absolutely fine if you'd like to contribute to the project. :)