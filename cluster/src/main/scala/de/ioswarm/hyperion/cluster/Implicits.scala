package de.ioswarm.hyperion.cluster

import de.ioswarm.hyperion.{CoreImplicits, ServiceBuilderImplicits}

object Implicits extends CoreImplicits
  with ClusterImplicits
  with ServiceBuilderImplicits
