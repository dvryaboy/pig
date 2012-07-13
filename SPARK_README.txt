Spork currently requires you to build Spark separately and publish it to your
local ivy cache. It relies on the "dev" branch of Spark. You can check this
out and publish it to your local Ivy as follows:

git clone git://github.com/mesos/spark.git
cd spark
git checkout -b dev --track origin/dev
sbt/sbt publish-local

Then build Spork using ant jar as usual.
