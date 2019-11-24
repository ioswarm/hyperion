package de.ioswarm.hyperion.utils

object Reflect {

  def classForName(name: String): Class[_] = {
    if (name.endsWith("$"))
      Class.forName(name).getField("MODULE$").get(null).getClass
    else
      Class.forName(name)
  }

  def instanceOf[T](clazzName: String)(implicit m: Manifest[T]): T = {
    if (clazzName.endsWith("$")) { // handle object
      Class.forName(clazzName).getField("MODULE$").get(m.runtimeClass).asInstanceOf[T]
    } else {
      Class.forName(clazzName).newInstance().asInstanceOf[T]
    }
  }

  def execPath[T](path: String): T = {
    val names = path.split("#")
    val clsinst: Any = instanceOf(names(0))
    val cls = classForName(names(0))

    if (names.size > 1) {
      cls.getMethods.find(m => m.getName == names(1) && m.getParameterCount == 0).map(_.invoke(clsinst)) -> cls.getFields.find(_.getName == names(1)) match {
        case (Some(res), _) => res.asInstanceOf[T]
        case (_, Some(res)) => res.asInstanceOf[T]
        case _ => clsinst.asInstanceOf[T]
      }
    } else clsinst.asInstanceOf[T]
  }

}
